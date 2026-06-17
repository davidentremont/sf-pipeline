package com.sfpipeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RuntimeParam {
    private String plugin;
    private String key;
    private String label;
    private String type = "text";
    private String placeholder;
    private boolean required = false;
    private String defaultValue;

    public String getPlugin() { return plugin; }
    public void setPlugin(String plugin) { this.plugin = plugin; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
}
