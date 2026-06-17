package com.sfpipeline.model;

public class PipelineConfig {
    private Job job;
    private String instanceUrl;
    private String accessToken;
    private int batchSize;
    private int threads;

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
}
