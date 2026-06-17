package com.sfpipeline.plugin;

import com.sfpipeline.model.Job;
import com.sfpipeline.service.SfdxService;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PluginContext {
    private final int workerId;
    private final String org;
    private final Job job;
    private final SfdxService sfdxService;
    private final Consumer<String> logger;

    public PluginContext(int workerId, String org, Job job, SfdxService sfdxService, Consumer<String> logger) {
        this.workerId = workerId;
        this.org = org;
        this.job = job;
        this.sfdxService = sfdxService;
        this.logger = logger;
    }

    public int getWorkerId() { return workerId; }
    public String getOrg() { return org; }
    public Job getJob() { return job; }

    /** Run an sfdx CLI command. Returns parsed JSON result. */
    public List<Map<String, Object>> runQuery(String soql) throws Exception {
        return sfdxService.runQuery(soql, org);
    }

    /** Run any sf CLI command and return stdout as a string. */
    public String runCommand(String... args) throws Exception {
        return sfdxService.runCommand(org, args);
    }

    public void log(String message) {
        logger.accept("[Worker " + workerId + "] " + message);
    }
}
