package com.sfpipeline.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;

public class PipelineEvent {
    private String type;
    private long timestamp;
    private Map<String, Object> data;

    public PipelineEvent(String type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.data = new HashMap<>();
    }

    public PipelineEvent with(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    public String getType() { return type; }
    public long getTimestamp() { return timestamp; }

    @JsonAnyGetter
    @JsonIgnore
    public Map<String, Object> getData() { return data; }
}
