package com.sfpipeline.plugin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PluginRegistry {

    private final Map<String, Plugin> pluginsByName;

    @Autowired
    public PluginRegistry(List<Plugin> plugins) {
        this.pluginsByName = plugins.stream()
                .collect(Collectors.toMap(Plugin::getName, Function.identity()));
    }

    public Plugin getPlugin(String name) {
        Plugin plugin = pluginsByName.get(name);
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin not found: '" + name + "'. Available: " + pluginsByName.keySet());
        }
        return plugin;
    }

    public List<Plugin> getPlugins(List<String> names) {
        return names.stream().map(this::getPlugin).collect(Collectors.toList());
    }

    public List<String> listPluginNames() {
        return pluginsByName.keySet().stream().sorted().collect(Collectors.toList());
    }

    public List<Map<String, String>> listPluginInfo() {
        return pluginsByName.values().stream()
                .map(p -> Map.of(
                        "name", p.getName(),
                        "version", p.getVersion(),
                        "description", p.getDescription()))
                .collect(Collectors.toList());
    }
}
