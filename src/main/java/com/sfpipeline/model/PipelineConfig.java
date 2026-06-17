package com.sfpipeline.model;

public class PipelineConfig {
    private Job job;
    private String org;
    private int batchSize;
    private int threads;

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }
}
