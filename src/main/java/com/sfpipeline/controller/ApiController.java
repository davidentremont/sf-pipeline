package com.sfpipeline.controller;

import com.sfpipeline.model.Job;
import com.sfpipeline.model.PluginError;
import com.sfpipeline.model.PipelineProgress;
import com.sfpipeline.pipeline.PipelineEngine;
import com.sfpipeline.plugin.PluginRegistry;
import com.sfpipeline.service.ErrorService;
import com.sfpipeline.service.JobService;
import com.sfpipeline.service.ProgressService;
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
}
