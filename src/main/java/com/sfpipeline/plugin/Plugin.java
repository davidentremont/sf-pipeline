package com.sfpipeline.plugin;

import java.util.List;
import java.util.Map;

/**
 * Plugin interface for the SF Pipeline framework.
 *
 * Implement this interface and annotate with @Component to register a plugin.
 * Plugins chain sequentially: the output of one becomes the input of the next.
 * Step 0 input is the raw Salesforce query result records.
 */
public interface Plugin {

    /** Unique plugin name — referenced in job configs by this value. */
    String getName();

    String getVersion();

    String getDescription();

    /**
     * Execute the plugin for one worker's chunk of records.
     *
     * @param input   Output from the previous plugin (or query records for step 0)
     * @param context Provides org, workerId, sfdx command runner, and logger
     * @return Data to pass to the next plugin (or discarded if this is the last plugin)
     */
    List<Map<String, Object>> execute(List<Map<String, Object>> input, PluginContext context) throws Exception;
}
