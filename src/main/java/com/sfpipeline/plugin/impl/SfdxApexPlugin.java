package com.sfpipeline.plugin.impl;

import com.sfpipeline.plugin.Plugin;
import com.sfpipeline.plugin.PluginContext;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sample plugin: runs an Apex script via the SF CLI for each chunk of records.
 * Demonstrates how to call sfdx commands from a plugin.
 */
@Component
public class SfdxApexPlugin implements Plugin {

    @Override
    public String getName() { return "SfdxApexPlugin"; }

    @Override
    public String getVersion() { return "1.0"; }

    @Override
    public String getDescription() { return "Runs an anonymous Apex script for each record chunk via the SF CLI"; }

    @Override
    public List<Map<String, Object>> execute(List<Map<String, Object>> input, PluginContext context) throws Exception {
        List<String> ids = input.stream()
                .map(r -> "'" + r.get("Id") + "'")
                .collect(Collectors.toList());

        String idList = String.join(",", ids);
        String apex = String.format(
            "List<Id> recordIds = new List<Id>{%s};\n" +
            "System.debug('Worker %d processing ' + recordIds.size() + ' records');\n",
            idList, context.getWorkerId()
        );

        java.io.File tmpFile = java.io.File.createTempFile("apex_worker_" + context.getWorkerId() + "_", ".apex");
        try {
            java.nio.file.Files.writeString(tmpFile.toPath(), apex);
            String result = context.runCommand("apex", "run", "--file", tmpFile.getAbsolutePath());
            context.log("Apex executed. Result: " + result.substring(0, Math.min(result.length(), 200)));
        } finally {
            tmpFile.delete();
        }

        return input;
    }
}
