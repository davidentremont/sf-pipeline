package com.sfpipeline.model;

public class PipelineProgress {
    private String jobId;
    private String instanceUrl;
    private String query;
    private String lastId;
    private long totalProcessed;
    private int batchNum;
    private String status;
    private long totalCount;
    private String startedAt;
    private String updatedAt;
    private String finishedAt;

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getInstanceUrl() { return instanceUrl; }
    public void setInstanceUrl(String instanceUrl) { this.instanceUrl = instanceUrl; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getLastId() { return lastId; }
    public void setLastId(String lastId) { this.lastId = lastId; }

    public long getTotalProcessed() { return totalProcessed; }
    public void setTotalProcessed(long totalProcessed) { this.totalProcessed = totalProcessed; }

    public int getBatchNum() { return batchNum; }
    public void setBatchNum(int batchNum) { this.batchNum = batchNum; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
}
