package com.sfpipeline.plugin;

import com.sfpipeline.model.Job;
import com.sfpipeline.service.ErrorService;
import com.sfpipeline.service.SalesforceService;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.Collections;

public class PluginContext {
    private final int workerId;
    private final String instanceUrl;
    private final String accessToken;
    private final Job job;
    private final SalesforceService salesforceService;
    private final Consumer<String> logger;
    private final ErrorService errorService;
    private final String pluginName;

    public PluginContext(int workerId, String instanceUrl, String accessToken,
                         Job job, SalesforceService salesforceService, Consumer<String> logger,
                         ErrorService errorService, String pluginName) {
        this.workerId = workerId;
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.job = job;
        this.salesforceService = salesforceService;
        this.logger = logger;
        this.errorService = errorService;
        this.pluginName = pluginName;
    }

    public int getWorkerId() { return workerId; }
    public String getInstanceUrl() { return instanceUrl; }
    public String getAccessToken() { return accessToken; }
    public Job getJob() { return job; }

    public String getPluginParam(String pluginName, String key) {
        Map<String, Map<String, Object>> cfg = job.getPluginConfig();
        if (cfg == null) return null;
        Map<String, Object> pluginCfg = cfg.get(pluginName);
        if (pluginCfg == null) return null;
        Object val = pluginCfg.get(key);
        return val != null ? val.toString() : null;
    }

    public List<Map<String, Object>> runQuery(String soql) throws Exception {
        return salesforceService.runQuery(soql, instanceUrl, accessToken);
    }

    public void log(String message) {
        logger.accept("[Worker " + workerId + "] " + message);
    }

    public void recordError(String recordId, String message) {
        if (errorService != null) {
            errorService.recordError(job.getId(), instanceUrl, recordId, pluginName, message);
        }
    }
}
