package com.sfpipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SalesforceService {

    public static final String API_VERSION = "v64.0";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> runQuery(String soql, String instanceUrl, String accessToken) throws Exception {
        String urlStr = instanceUrl + "/services/data/" + API_VERSION + "/query?q="
                + URLEncoder.encode(soql, StandardCharsets.UTF_8);

        List<Map<String, Object>> result = new ArrayList<>();
        while (urlStr != null) {
            HttpURLConnection conn = openConnection(urlStr, "GET", accessToken);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            int code = conn.getResponseCode();
            String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            conn.disconnect();

            if (code < 200 || code >= 300) {
                throw new RuntimeException("Salesforce query failed (HTTP " + code + "): " + body);
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode records = root.path("records");
            if (records.isArray()) {
                for (JsonNode record : records) {
                    Map<String, Object> row = new HashMap<>();
                    record.fields().forEachRemaining(e -> {
                        if (!e.getKey().equals("attributes")) {
                            row.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText());
                        }
                    });
                    result.add(row);
                }
            }

            JsonNode nextUrl = root.path("nextRecordsUrl");
            urlStr = (!root.path("done").asBoolean(true) && !nextUrl.isMissingNode())
                    ? instanceUrl + nextUrl.asText()
                    : null;
        }
        return result;
    }

    public HttpURLConnection openConnection(String urlStr, String method, String accessToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    public String readAll(InputStream is) throws java.io.IOException {
        if (is == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
    }
}
