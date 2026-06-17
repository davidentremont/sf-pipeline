package com.sfpipeline.plugin.impl;

import com.sfpipeline.plugin.Plugin;
import com.sfpipeline.plugin.PluginContext;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/**
 * Sample plugin: logs each record's Id to the event stream.
 * Use this as a template for creating new plugins.
 */
@Component
public class LogPlugin implements Plugin {

    @Override
    public String getName() { return "LogPlugin"; }

    @Override
    public String getVersion() { return "1.0"; }

    @Override
    public String getDescription() { return "Logs record IDs for each worker chunk"; }

    @Override
    public List<Map<String, Object>> execute(List<Map<String, Object>> input, PluginContext context) {
        context.log("LogPlugin processing " + input.size() + " records");
        for (Map<String, Object> record : input) {
            context.log("  Record: " + record.get("Id"));
        }
        return input;
    }
}
