package com.sfpipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SfdxService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Run a SOQL query against a Salesforce org using the SF CLI.
     * Returns the records array from the query result.
     */
    public List<Map<String, Object>> runQuery(String soql, String org) throws Exception {
        String output = runSfCommand(new String[]{"data", "query",
                "--query", soql,
                "--target-org", org,
                "--json"});

        JsonNode root = objectMapper.readTree(output);
        int status = root.path("status").asInt(-1);
        if (status != 0) {
            String message = root.path("message").asText(root.path("data").path("message").asText("Unknown error"));
            throw new RuntimeException("SF CLI query failed: " + message);
        }

        JsonNode records = root.path("result").path("records");
        if (!records.isArray()) return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode record : records) {
            Map<String, Object> row = new HashMap<>();
            record.fields().forEachRemaining(entry -> {
                if (!entry.getKey().equals("attributes")) {
                    row.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText());
                }
            });
            result.add(row);
        }
        return result;
    }

    /**
     * Run any SF CLI command and return stdout as a string.
     * The --target-org flag is automatically appended when org is provided.
     */
    public String runCommand(String org, String... args) throws Exception {
        String[] fullArgs;
        if (org != null && !org.isBlank()) {
            fullArgs = new String[args.length + 2];
            System.arraycopy(args, 0, fullArgs, 0, args.length);
            fullArgs[args.length] = "--target-org";
            fullArgs[args.length + 1] = org;
        } else {
            fullArgs = args;
        }
        return runSfCommand(fullArgs);
    }

    private String runSfCommand(String[] args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "sf";
        System.arraycopy(args, 0, command, 1, args.length);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 && !output.contains("\"status\":")) {
            throw new RuntimeException("SF CLI exited with code " + exitCode + ": " + output);
        }
        return output;
    }
}
