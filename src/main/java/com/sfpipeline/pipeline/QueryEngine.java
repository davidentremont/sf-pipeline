package com.sfpipeline.pipeline;

import com.sfpipeline.service.SalesforceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class QueryEngine {

    @Autowired
    private SalesforceService salesforceService;

    public List<Map<String, Object>> query(String baseQuery, String lastId, int batchSize,
                                            String instanceUrl, String accessToken) throws Exception {
        return salesforceService.runQuery(buildQuery(baseQuery, lastId, batchSize), instanceUrl, accessToken);
    }

    String buildQuery(String baseQuery, String lastId, int batchSize) {
        String q = baseQuery.replaceAll("(?i)\\s+LIMIT\\s+\\d+", "").trim();

        if (lastId != null && !lastId.isBlank()) {
            String pkClause = "Id > '" + lastId + "'";
            boolean hasWhere = q.matches("(?si).*\\bWHERE\\b.*");
            int orderByPos = indexOfIgnoreCase(q, " ORDER BY ");
            if (orderByPos >= 0) {
                q = q.substring(0, orderByPos)
                        + (hasWhere ? " AND " : " WHERE ") + pkClause
                        + q.substring(orderByPos);
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
