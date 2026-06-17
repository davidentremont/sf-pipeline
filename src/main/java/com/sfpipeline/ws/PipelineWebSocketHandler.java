package com.sfpipeline.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfpipeline.model.Job;
import com.sfpipeline.model.PipelineConfig;
import com.sfpipeline.model.PipelineEvent;
import com.sfpipeline.pipeline.PipelineEngine;
import com.sfpipeline.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class PipelineWebSocketHandler extends TextWebSocketHandler {

    @Autowired private PipelineEngine pipelineEngine;
    @Autowired private JobService jobService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        send(session, new PipelineEvent("CONNECTED")
                .with("sessionId", session.getId())
                .with("running", pipelineEngine.isRunning()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode msg = objectMapper.readTree(message.getPayload());
        switch (msg.path("type").asText()) {
            case "START" -> handleStart(session, msg);
            case "STOP"  -> pipelineEngine.stop();
            default      -> send(session, new PipelineEvent("ERROR")
                    .with("message", "Unknown command: " + msg.path("type").asText()));
        }
    }

    private void handleStart(WebSocketSession session, JsonNode msg) {
        if (pipelineEngine.isRunning()) {
            send(session, new PipelineEvent("ERROR").with("message", "Pipeline is already running"));
            return;
        }

        String jobId       = msg.path("jobId").asText();
        String instanceUrl = msg.path("instanceUrl").asText();
        String accessToken = msg.path("accessToken").asText();
        int batchSize      = msg.path("batchSize").asInt(1000);
        int threads        = msg.path("threads").asInt(5);

        if (instanceUrl.isBlank()) {
            send(session, new PipelineEvent("ERROR").with("message", "Instance URL is required"));
            return;
        }
        if (accessToken.isBlank()) {
            send(session, new PipelineEvent("ERROR").with("message", "Access token is required"));
            return;
        }

        Job job;
        try {
            job = jobService.getJobById(jobId);
        } catch (Exception e) {
            send(session, new PipelineEvent("ERROR").with("message", "Job not found: " + jobId));
            return;
        }

        // Merge runtime params into pluginConfig; also collect flat map for query substitution
        Map<String, String> queryTokens = new HashMap<>();
        JsonNode params = msg.path("params");
        if (params.isObject()) {
            Map<String, Map<String, Object>> pluginConfig = job.getPluginConfig();
            if (pluginConfig == null) pluginConfig = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> plugins = params.fields();
            while (plugins.hasNext()) {
                Map.Entry<String, JsonNode> entry = plugins.next();
                Map<String, Object> cfg = pluginConfig.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
                entry.getValue().fields().forEachRemaining(f -> {
                    cfg.put(f.getKey(), f.getValue().asText());
                    queryTokens.put(f.getKey(), f.getValue().asText());
                });
            }
            job.setPluginConfig(pluginConfig);
        }
        // Substitute {key} tokens in the query with runtime param values
        if (!queryTokens.isEmpty() && job.getQuery() != null) {
            String query = job.getQuery();
            for (Map.Entry<String, String> token : queryTokens.entrySet()) {
                query = query.replace("{" + token.getKey() + "}", token.getValue());
            }
            job.setQuery(query);
        }

        PipelineConfig config = new PipelineConfig();
        config.setJob(job);
        config.setInstanceUrl(instanceUrl);
        config.setAccessToken(accessToken);
        config.setBatchSize(batchSize);
        config.setThreads(threads);

        pipelineEngine.start(config, this::broadcast);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
    }

    public void broadcast(PipelineEvent event) {
        if (sessions.isEmpty()) return;
        try {
            TextMessage msg = new TextMessage(objectMapper.writeValueAsString(event));
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        try { session.sendMessage(msg); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Broadcast failed: " + e.getMessage());
        }
    }

    private void send(WebSocketSession session, PipelineEvent event) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            System.err.println("Send failed: " + e.getMessage());
        }
    }
}
