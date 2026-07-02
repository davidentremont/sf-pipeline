package com.sfpipeline.model;

public class PipelineConfig {
    private Job job;
    private String instanceUrl;
    private String accessToken;
    private int batchSize;
    private int threads;
    private String resumeFromId;
    private long initialProcessed;
    private java.util.List<String> retryIds;

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public String getInstanceUrl() { return instanceUrl; }
    public void setInstanceUrl(String instanceUrl) { this.instanceUrl = instanceUrl; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }

    public String getResumeFromId() { return resumeFromId; }
    public void setResumeFromId(String resumeFromId) { this.resumeFromId = resumeFromId; }

    public long getInitialProcessed() { return initialProcessed; }
    public void setInitialProcessed(long initialProcessed) { this.initialProcessed = initialProcessed; }

    public java.util.List<String> getRetryIds() { return retryIds; }
    public void setRetryIds(java.util.List<String> retryIds) { this.retryIds = retryIds; }
}
