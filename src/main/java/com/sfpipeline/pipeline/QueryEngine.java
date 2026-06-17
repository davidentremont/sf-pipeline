package com.sfpipeline.pipeline;

import com.sfpipeline.service.SfdxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class QueryEngine {

    @Autowired
    private SfdxService sfdxService;

    public List<Map<String, Object>> query(String baseQuery, String lastId, int batchSize, String org) throws Exception {
        String query = buildQuery(baseQuery, lastId, batchSize);
        return sfdxService.runQuery(query, org);
    }

    /**
     * Injects a PK-chunk WHERE clause and LIMIT into the base query.
     *
     * Input:  SELECT Id, Name FROM Account ORDER BY Id ASC
     * First:  SELECT Id, Name FROM Account ORDER BY Id ASC LIMIT 1000
     * After:  SELECT Id, Name FROM Account WHERE Id > '001...' ORDER BY Id ASC LIMIT 1000
     */
    String buildQuery(String baseQuery, String lastId, int batchSize) {
        // Strip any existing LIMIT clause
        String q = baseQuery.replaceAll("(?i)\\s+LIMIT\\s+\\d+", "").trim();

        if (lastId != null && !lastId.isBlank()) {
            String pkClause = "Id > '" + lastId + "'";
            boolean hasWhere = q.matches("(?si).*\\bWHERE\\b.*");

            // Find position of ORDER BY to insert before it
            int orderByPos = indexOfIgnoreCase(q, " ORDER BY ");
            if (orderByPos >= 0) {
                String before = q.substring(0, orderByPos);
                String after = q.substring(orderByPos);
                q = before + (hasWhere ? " AND " : " WHERE ") + pkClause + after;
            } else {
                q = q + (hasWhere ? " AND " : " WHERE ") + pkClause;
            }
        }

        return q + " LIMIT " + batchSize;
    }

    private int indexOfIgnoreCase(String text, String search) {
        return text.toUpperCase().indexOf(search.toUpperCase());
    }
}
