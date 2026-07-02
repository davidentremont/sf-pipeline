package com.sfpipeline.model;

public class SfOrg {
    private String alias;
    private String username;
    private String instanceUrl;
    private String connectedStatus;
    private boolean defaultUsername;

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getInstanceUrl() { return instanceUrl; }
    public void setInstanceUrl(String instanceUrl) { this.instanceUrl = instanceUrl; }

    public String getConnectedStatus() { return connectedStatus; }
    public void setConnectedStatus(String connectedStatus) { this.connectedStatus = connectedStatus; }

    public boolean isDefaultUsername() { return defaultUsername; }
    public void setDefaultUsername(boolean defaultUsername) { this.defaultUsername = defaultUsername; }
}
