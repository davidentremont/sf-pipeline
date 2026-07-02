package com.sfpipeline.plugin.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfpipeline.plugin.Plugin;
import com.sfpipeline.plugin.PluginContext;

@Component
public class ShareCalculatorPlugin implements Plugin {

    private static final String API_VERSION = "v64.0";
    private static final int DEFAULT_BATCH_SIZE = 50;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() { return "ShareCalculatorPlugin"; }

    @Override
    public String getVersion() { return "1.0"; }

    @Override
    public String getDescription() {
        return "Recalculates Salesforce sharing via sobjectshares Apex REST endpoint and inserts resulting share records. Requires class access to GS_A";
    }

    @Override
    public List<Map<String, Object>> execute(List<Map<String, Object>> input, PluginContext context) throws Exception {
        if (input.isEmpty()) return input;

        Map<String, Object> config = pluginConfig(context);
        String objectType = (String) config.getOrDefault("objectType", "Case");
        int batchSize = ((Number) config.getOrDefault("batchSize", DEFAULT_BATCH_SIZE)).intValue();

        String instanceUrl = context.getInstanceUrl();
        String accessToken = context.getAccessToken();

        context.log("ShareCalculatorPlugin: objectType=" + objectType + ", batchSize=" + batchSize);

        List<String> ids = input.stream()
                .map(r -> (String) r.get("Id"))
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        if (ids.isEmpty()) return input;

        int totalInserted = 0;
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<String> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            totalInserted += processBatch(batch, objectType, instanceUrl, accessToken, context);
        }

        context.log("ShareCalculatorPlugin: inserted " + totalInserted + " share record(s) for " + ids.size() + " record(s)");
        return input;
    }

    private Map<String, Object> pluginConfig(PluginContext context) {
        Map<String, Map<String, Object>> all = context.getJob().getPluginConfig();
        if (all != null && all.containsKey(getName())) return all.get(getName());
        return Collections.emptyMap();
    }

    private int processBatch(List<String> ids, String objectType, String instanceUrl,
                              String accessToken, PluginContext context) throws Exception {
        String urlStr = instanceUrl + "/services/apexrest/sobjectshares"
                + "?objectApiName=" + objectType
                + "&recordIds=" + String.join(",", ids);

        HttpURLConnection conn = openConnection(urlStr, "GET", accessToken);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(300000);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String errBody = readAll(conn.getErrorStream());
            conn.disconnect();
            context.log("Warning: sobjectshares returned HTTP " + code + ": " + errBody);
            String errMsg = "sobjectshares HTTP " + code + ": " + errBody;
            for (String id : ids) context.recordError(id, errMsg);
            return 0;
        }

        String resp = readAll(conn.getInputStream());
        conn.disconnect();

        List<String> shareRecords = extractRecords(resp);
        if (shareRecords.isEmpty()) return 0;

        int inserted = 0;
        for (int i = 0; i < shareRecords.size(); i += 200) {
            inserted += insertBatch(shareRecords.subList(i, Math.min(i + 200, shareRecords.size())),
                    instanceUrl, accessToken, context);
        }
        return inserted;
    }

    private int insertBatch(List<String> records, String instanceUrl, String accessToken,
                             PluginContext context) throws Exception {
        StringBuilder body = new StringBuilder("{\"records\":[");
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) body.append(',');
            body.append(records.get(i));
        }
        body.append("]}");

        String urlStr = instanceUrl + "/services/data/" + API_VERSION + "/composite/sobjects?allOrNone=false";
        HttpURLConnection conn = openConnection(urlStr, "POST", accessToken);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(300000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            context.log("Warning: composite insert returned HTTP " + code + ": " + readAll(conn.getErrorStream()));
            conn.disconnect();
            return 0;
        }
        conn.disconnect();
        return records.size();
    }

    private HttpURLConnection openConnection(String urlStr, String method, String accessToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    private List<String> extractRecords(String json) throws Exception {
        List<String> out = new ArrayList<>();
        JsonNode records = objectMapper.readTree(json).path("records");
        if (records.isArray()) {
            for (JsonNode record : records) out.add(objectMapper.writeValueAsString(record));
        }
        return out;
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
    }
}
