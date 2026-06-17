package com.sfpipeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Job {
    private String id;
    private String name;
    private String version;
    private String description;
    private String query;
    private List<String> plugins;
    private int defaultBatchSize = 1000;
    private int defaultThreads = 5;
    private Map<String, Map<String, Object>> pluginConfig;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<String> getPlugins() { return plugins; }
    public void setPlugins(List<String> plugins) { this.plugins = plugins; }

    public int getDefaultBatchSize() { return defaultBatchSize; }
    public void setDefaultBatchSize(int defaultBatchSize) { this.defaultBatchSize = defaultBatchSize; }

    public int getDefaultThreads() { return defaultThreads; }
    public void setDefaultThreads(int defaultThreads) { this.defaultThreads = defaultThreads; }

    public Map<String, Map<String, Object>> getPluginConfig() { return pluginConfig; }
    public void setPluginConfig(Map<String, Map<String, Object>> pluginConfig) { this.pluginConfig = pluginConfig; }
}
