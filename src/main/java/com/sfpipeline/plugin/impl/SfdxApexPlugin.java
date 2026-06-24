package com.sfpipeline.plugin.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfpipeline.plugin.Plugin;
import com.sfpipeline.plugin.PluginContext;
import com.sfpipeline.service.SalesforceService;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SfdxApexPlugin implements Plugin {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() { return "SfdxApexPlugin"; }

    @Override
    public String getVersion() { return "1.0"; }

    @Override
    public String getDescription() { return "Runs anonymous Apex via the Salesforce Tooling API REST endpoint"; }

    @Override
    public List<Map<String, Object>> execute(List<Map<String, Object>> input, PluginContext context) throws Exception {
        List<String> ids = input.stream()
                .map(r -> "'" + r.get("Id") + "'")
                .collect(Collectors.toList());

        String idsLiteral = String.join(",", ids);
        String apexTemplate = context.getPluginParam("SfdxApexPlugin", "apexCode");
        String apex;
        if (apexTemplate != null && !apexTemplate.isBlank()) {
            apex = apexTemplate
                    .replace("{ids}", idsLiteral)
                    .replace("{workerId}", String.valueOf(context.getWorkerId()));
        } else {
            apex = String.format(
                    "List<Id> recordIds = new List<Id>{%s};\n" +
                    "System.debug('Worker %d processing ' + recordIds.size() + ' records');",
                    idsLiteral, context.getWorkerId());
        }

        String urlStr = context.getInstanceUrl()
                + "/services/data/" + SalesforceService.API_VERSION
                + "/tooling/executeAnonymous?anonymousBody="
                + URLEncoder.encode(apex, StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + context.getAccessToken());
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        String body = code >= 200 && code < 300
                ? new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                : new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new RuntimeException("Tooling API returned HTTP " + code + ": " + body);
        }

        JsonNode result = objectMapper.readTree(body);
        if (!result.path("success").asBoolean()) {
            String problem = result.path("compileProblem").asText(result.path("exceptionMessage").asText("unknown error"));
            throw new RuntimeException("Apex execution failed: " + problem);
        }

        context.log("Apex executed for " + input.size() + " records");
        return input;
    }
}
