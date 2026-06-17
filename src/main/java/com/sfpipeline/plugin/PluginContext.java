package com.sfpipeline.plugin;

import com.sfpipeline.model.Job;
import com.sfpipeline.service.SalesforceService;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PluginContext {
    private final int workerId;
    private final String instanceUrl;
    private final String accessToken;
    private final Job job;
    private final SalesforceService salesforceService;
    private final Consumer<String> logger;

    public PluginContext(int workerId, String instanceUrl, String accessToken,
                         Job job, SalesforceService salesforceService, Consumer<String> logger) {
        this.workerId = workerId;
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.job = job;
        this.salesforceService = salesforceService;
        this.logger = logger;
    }

    public int getWorkerId() { return workerId; }
    public String getInstanceUrl() { return instanceUrl; }
    public String getAccessToken() { return accessToken; }
    public Job getJob() { return job; }

    public List<Map<String, Object>> runQuery(String soql) throws Exception {
        return salesforceService.runQuery(soql, instanceUrl, accessToken);
    }

    public void log(String message) {
        logger.accept("[Worker " + workerId + "] " + message);
    }
}
