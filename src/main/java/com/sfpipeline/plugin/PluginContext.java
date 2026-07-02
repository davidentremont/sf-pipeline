package com.sfpipeline.plugin;

import com.sfpipeline.model.Job;
import com.sfpipeline.service.SalesforceService;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.Collections;

public class PluginContext {
    private final int workerId;
    private final String instanceUrl;
    private final String accessToken;
    private final Job job;
    private final SalesforceService salesforceService;
    private final Consumer<String> logger;
    private final BiConsumer<String, String> errorRecorder;

    public PluginContext(int workerId, String instanceUrl, String accessToken,
                         Job job, SalesforceService salesforceService, Consumer<String> logger,
                         BiConsumer<String, String> errorRecorder) {
        this.workerId = workerId;
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.job = job;
        this.salesforceService = salesforceService;
        this.logger = logger;
        this.errorRecorder = errorRecorder;
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
        if (errorRecorder != null) {
            errorRecorder.accept(recordId, message);
        }
    }
}
