package com.sfpipeline.plugin.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfpipeline.plugin.Plugin;
import com.sfpipeline.plugin.PluginContext;
import com.sfpipeline.service.SalesforceService;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CompositeDeletePlugin implements Plugin {

    private static final int MAX_IDS_PER_REQUEST = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() { return "CompositeDeletePlugin"; }

    @Override
    public String getVersion() { return "1.0"; }

    @Override
    public String getDescription() {
        return "Deletes records via the Salesforce Composite API (DELETE /composite/sobjects)";
    }

    @Override
    public List<Map<String, Object>> execute(List<Map<String, Object>> input, PluginContext context) throws Exception {
        if (input.isEmpty()) return input;

        Map<String, Object> config = pluginConfig(context);
        String objectType = (String) config.getOrDefault("objectType", "");
        boolean allOrNone = Boolean.parseBoolean(config.getOrDefault("allOrNone", false).toString());

        List<String> ids = input.stream()
                .map(r -> (String) r.get("Id"))
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        if (ids.isEmpty()) return input;

        context.log("CompositeDeletePlugin: objectType=" + (objectType.isBlank() ? "(from query)" : objectType)
                + ", allOrNone=" + allOrNone + ", records=" + ids.size());

        int totalDeleted = 0;
        int totalFailed  = 0;

        for (int i = 0; i < ids.size(); i += MAX_IDS_PER_REQUEST) {
            List<String> batch = ids.subList(i, Math.min(i + MAX_IDS_PER_REQUEST, ids.size()));
            int[] counts = deleteBatch(batch, allOrNone, context);
            totalDeleted += counts[0];
            totalFailed  += counts[1];
        }

        context.log("CompositeDeletePlugin: deleted=" + totalDeleted + ", failed=" + totalFailed
                + " (of " + ids.size() + " records)");

        return input;
    }

    private int[] deleteBatch(List<String> ids, boolean allOrNone, PluginContext context) throws Exception {
        String urlStr = context.getInstanceUrl()
                + "/services/data/" + SalesforceService.API_VERSION
                + "/composite/sobjects?ids=" + String.join(",", ids)
                + "&allOrNone=" + allOrNone;

        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + context.getAccessToken());
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = readAll(conn.getErrorStream());
            conn.disconnect();
            context.log("Warning: composite delete returned HTTP " + code + ": " + err);
            String errMsg = "composite delete HTTP " + code + ": " + err;
            for (String id : ids) context.recordError(id, errMsg);
            return new int[]{0, ids.size()};
        }

        String body = readAll(conn.getInputStream());
        conn.disconnect();

        return countResults(body, context);
    }

    private int[] countResults(String body, PluginContext context) throws Exception {
        JsonNode results = objectMapper.readTree(body);
        if (!results.isArray()) return new int[]{0, 0};

        int deleted = 0;
        int failed  = 0;
        List<String> logErrors = new ArrayList<>();

        for (JsonNode r : results) {
            if (r.path("success").asBoolean(false)) {
                deleted++;
            } else {
                failed++;
                String recordId = r.path("id").asText("");
                JsonNode errs = r.path("errors");
                String errMsg = errs.isArray() && errs.size() > 0
                        ? errs.get(0).path("message").asText("delete failed")
                        : "delete failed";
                if (!recordId.isBlank()) {
                    context.recordError(recordId, errMsg);
                    logErrors.add(recordId + ": " + errMsg);
                }
            }
        }

        if (!logErrors.isEmpty()) {
            context.log("Delete errors: " + String.join("; ", logErrors.subList(0, Math.min(5, logErrors.size())))
                    + (logErrors.size() > 5 ? " (+" + (logErrors.size() - 5) + " more)" : ""));
        }

        return new int[]{deleted, failed};
    }

    private Map<String, Object> pluginConfig(PluginContext context) {
        Map<String, Map<String, Object>> all = context.getJob().getPluginConfig();
        if (all != null && all.containsKey(getName())) return all.get(getName());
        return Collections.emptyMap();
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
    }
}
