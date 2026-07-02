package com.sfpipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfpipeline.model.SfOrg;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SfCliService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SfOrg> listOrgs() throws Exception {
        String output = runSf("org", "list", "--json");
        JsonNode root = objectMapper.readTree(output);
        JsonNode result = root.path("result");

        // Use LinkedHashMap keyed by username to deduplicate across CLI category keys
        LinkedHashMap<String, SfOrg> seen = new LinkedHashMap<>();

        result.fields().forEachRemaining(entry -> {
            JsonNode arr = entry.getValue();
            if (!arr.isArray()) return;
            for (JsonNode n : arr) {
                String username = n.path("username").asText("");
                if (username.isBlank() || seen.containsKey(username)) return;
                SfOrg org = new SfOrg();
                org.setAlias(n.path("alias").asText(""));
                org.setUsername(username);
                org.setInstanceUrl(n.path("instanceUrl").asText(""));
                org.setConnectedStatus(n.path("connectedStatus").asText("Unknown"));
                org.setDefaultUsername(n.path("isDefaultUsername").asBoolean(false)
                        || n.path("isDefaultDevHubUsername").asBoolean(false));
                seen.put(username, org);
            }
        });
        return new ArrayList<>(seen.values());
    }

    public Map<String, String> displayOrg(String aliasOrUsername) throws Exception {
        // sf org display gives metadata; access token is separate in SF CLI v2
        String displayOut = runSf("org", "display", "--target-org", aliasOrUsername, "--json");
        JsonNode display = objectMapper.readTree(displayOut).path("result");

        String tokenOut = runSf("org", "auth", "show-access-token", "--target-org", aliasOrUsername, "--json");
        JsonNode tokenResult = objectMapper.readTree(tokenOut).path("result");

        Map<String, String> info = new HashMap<>();
        info.put("instanceUrl", display.path("instanceUrl").asText(""));
        info.put("accessToken", tokenResult.path("accessToken").asText(
                display.path("accessToken").asText("")));  // fallback for older CLI
        info.put("username", display.path("username").asText(""));
        info.put("alias", display.path("alias").asText(""));
        return info;
    }

    public boolean isAvailable() {
        try {
            runSf("--version");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String runSf(String... args) throws Exception {
        // Try `sf` first, fall back to `sfdx`
        try {
            return run("sf", args);
        } catch (Exception e) {
            try {
                return run("sfdx", args);
            } catch (Exception ignored) {}
            throw e;
        }
    }

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private String run(String cli, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        if (IS_WINDOWS) {
            // On Windows, wrap in cmd.exe so PATH (including %APPDATA%\npm) is resolved
            StringBuilder cmd = new StringBuilder(quoteWin(cli));
            for (String arg : args) cmd.append(' ').append(quoteWin(arg));
            command.add("cmd.exe");
            command.add("/c");
            command.add(cmd.toString());
        } else {
            // On macOS/Linux, use a login bash so Homebrew/nvm paths are on PATH
            StringBuilder cmd = new StringBuilder(shellQuote(cli));
            for (String arg : args) cmd.append(' ').append(shellQuote(arg));
            command.add("/bin/bash");
            command.add("-l");
            command.add("-c");
            command.add(cmd.toString());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        // Capture stdout only — stderr carries CLI update warnings that corrupt JSON.
        // Force UTF-8 so Windows CP1252 doesn't corrupt the JSON output.
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        int exitCode = process.waitFor();
        if (output.isBlank() && exitCode != 0) {
            // Drain stderr for a useful error message when stdout was empty
            String err;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                err = reader.lines().collect(Collectors.joining("\n"));
            }
            throw new RuntimeException(cli + " exited " + exitCode + (err.isBlank() ? "" : ": " + err.trim()));
        }
        return output;
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String quoteWin(String s) {
        // Wrap in double-quotes and escape internal double-quotes for cmd.exe
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }
}
