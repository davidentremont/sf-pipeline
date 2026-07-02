package com.sfpipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfpipeline.model.SfOrg;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SfCliService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SfOrg> listOrgs() throws Exception {
        String output = runSf("org", "list", "--json");
        JsonNode root = objectMapper.readTree(output);
        List<SfOrg> orgs = new ArrayList<>();

        for (String key : new String[]{"nonScratchOrgs", "scratchOrgs"}) {
            JsonNode arr = root.path("result").path(key);
            if (!arr.isArray()) continue;
            for (JsonNode n : arr) {
                SfOrg org = new SfOrg();
                org.setAlias(n.path("alias").asText(""));
                org.setUsername(n.path("username").asText(""));
                org.setInstanceUrl(n.path("instanceUrl").asText(""));
                org.setConnectedStatus(n.path("connectedStatus").asText("Unknown"));
                org.setDefaultUsername(n.path("isDefaultUsername").asBoolean(false));
                if (!org.getUsername().isBlank()) orgs.add(org);
            }
        }
        return orgs;
    }

    public Map<String, String> displayOrg(String aliasOrUsername) throws Exception {
        String output = runSf("org", "display", "--target-org", aliasOrUsername, "--json");
        JsonNode result = objectMapper.readTree(output).path("result");

        Map<String, String> info = new HashMap<>();
        info.put("instanceUrl", result.path("instanceUrl").asText(""));
        info.put("accessToken", result.path("accessToken").asText(""));
        info.put("username", result.path("username").asText(""));
        info.put("alias", result.path("alias").asText(""));
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
            if (e.getMessage() != null && e.getMessage().contains("No such file")) {
                return run("sfdx", args);
            }
            throw e;
        }
    }

    private String run(String cli, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = cli;
        System.arraycopy(args, 0, command, 1, args.length);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        process.waitFor();
        return output;
    }
}
