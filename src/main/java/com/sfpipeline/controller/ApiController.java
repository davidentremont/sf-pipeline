package com.sfpipeline.controller;

import com.sfpipeline.model.Job;
import com.sfpipeline.model.PluginError;
import com.sfpipeline.model.PipelineProgress;
import com.sfpipeline.model.SfOrg;
import com.sfpipeline.pipeline.PipelineEngine;
import com.sfpipeline.plugin.PluginRegistry;
import com.sfpipeline.service.ErrorService;
import com.sfpipeline.service.JobService;
import com.sfpipeline.service.ProgressService;
import com.sfpipeline.service.SalesforceService;
import com.sfpipeline.service.SfCliService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private JobService jobService;

    @Autowired
    private PluginRegistry pluginRegistry;

    @Autowired
    private PipelineEngine pipelineEngine;

    @Autowired
    private ProgressService progressService;

    @Autowired
    private ErrorService errorService;

    @Autowired
    private SfCliService sfCliService;

    @Autowired
    private SalesforceService salesforceService;

    @GetMapping("/jobs")
    public List<Job> getJobs() {
        return jobService.getJobs();
    }

    @GetMapping("/jobs/{id}")
    public Job getJob(@PathVariable String id) {
        return jobService.getJobById(id);
    }

    @PostMapping("/jobs/reload")
    public ResponseEntity<Map<String, Object>> reloadJobs() {
        jobService.reload();
        return ResponseEntity.ok(Map.of(
                "message", "Jobs reloaded",
                "count", jobService.getJobs().size()));
    }

    @GetMapping("/plugins")
    public List<Map<String, String>> getPlugins() {
        return pluginRegistry.listPluginInfo();
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of("running", pipelineEngine.isRunning());
    }

    @GetMapping("/progress")
    public List<PipelineProgress> getProgress() {
        return progressService.getAll();
    }

    @GetMapping("/errors")
    public List<PluginError> getErrors(@RequestParam String jobId, @RequestParam String instanceUrl) {
        return errorService.getErrors(jobId, instanceUrl);
    }

    @DeleteMapping("/errors")
    public ResponseEntity<Map<String, Object>> clearErrors(@RequestParam String jobId,
                                                            @RequestParam String instanceUrl) {
        errorService.clearErrors(jobId, instanceUrl);
        return ResponseEntity.ok(Map.of("message", "Errors cleared"));
    }

    @GetMapping("/orgs")
    public ResponseEntity<Map<String, Object>> getOrgs() {
        try {
            List<SfOrg> orgs = sfCliService.listOrgs();
            return ResponseEntity.ok(Map.of("orgs", orgs, "available", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("orgs", List.of(), "available", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/orgs/token")
    public ResponseEntity<Map<String, Object>> getOrgToken(@RequestBody Map<String, String> body) {
        String target = body.getOrDefault("alias", body.getOrDefault("username", ""));
        if (target.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "alias or username required"));
        try {
            Map<String, String> info = sfCliService.displayOrg(target);
            if (info.get("accessToken").isBlank()) {
                return ResponseEntity.status(404).body(Map.of("error", "No access token found for org — try re-authenticating with sf org login"));
            }
            return ResponseEntity.ok(Map.of(
                "instanceUrl", info.get("instanceUrl"),
                "accessToken", info.get("accessToken"),
                "username", info.get("username"),
                "alias", info.get("alias")
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyConnection(@RequestBody Map<String, String> body) {
        String instanceUrl = body.getOrDefault("instanceUrl", "");
        String accessToken = body.getOrDefault("accessToken", "");
        if (instanceUrl.isBlank() || accessToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "instanceUrl and accessToken required"));
        }
        try {
            Map<String, String> info = salesforceService.verifyConnection(instanceUrl, accessToken);
            return ResponseEntity.ok(Map.of("success", true, "info", info));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
