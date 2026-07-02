package com.sfpipeline.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfpipeline.model.Job;
import com.sfpipeline.model.PipelineConfig;
import com.sfpipeline.model.PipelineEvent;
import com.sfpipeline.model.PipelineProgress;
import com.sfpipeline.pipeline.PipelineEngine;
import com.sfpipeline.service.JobService;
import com.sfpipeline.service.ErrorService;
import com.sfpipeline.service.ProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class PipelineWebSocketHandler extends TextWebSocketHandler {

    @Autowired private PipelineEngine pipelineEngine;
    @Autowired private JobService jobService;
    @Autowired private ProgressService progressService;
    @Autowired private ErrorService errorService;

    @Value("${pipeline.max-workers:0}")
    private int maxWorkers;

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
            case "RETRY" -> handleRetry(session, msg);
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
        if (maxWorkers > 0 && threads > maxWorkers) threads = maxWorkers;
        boolean fresh      = msg.path("fresh").asBoolean(false);

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

        // Merge runtime params into pluginConfig; collect flat map for query substitution
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
        if (!queryTokens.isEmpty() && job.getQuery() != null) {
            String query = job.getQuery();
            for (Map.Entry<String, String> token : queryTokens.entrySet()) {
                query = query.replace("{" + token.getKey() + "}", token.getValue());
            }
            job.setQuery(query);
        }

        String resolvedQuery = job.getQuery();

        // Determine resume state
        String resumeFromId = null;
        long initialProcessed = 0;
        Optional<PipelineProgress> existing = progressService.get(jobId, instanceUrl);

        if (!fresh && existing.isPresent()) {
            PipelineProgress prev = existing.get();
            if (resolvedQuery != null && resolvedQuery.equals(prev.getQuery())
                    && prev.getLastId() != null
                    && !"completed".equals(prev.getStatus())) {
                resumeFromId = prev.getLastId();
                initialProcessed = prev.getTotalProcessed();
            }
        }

        // Clear stale errors when starting fresh
        if (fresh) {
            errorService.clearErrors(jobId, instanceUrl);
        }

        // Upsert progress record for this run
        PipelineProgress progress = new PipelineProgress();
        progress.setJobId(jobId);
        progress.setInstanceUrl(instanceUrl);
        progress.setQuery(resolvedQuery);
        progress.setLastId(resumeFromId);
        progress.setTotalProcessed(initialProcessed);
        progress.setBatchNum(existing.isPresent() && resumeFromId != null ? existing.get().getBatchNum() : 0);
        progress.setStatus("running");
        progress.setStartedAt(
            existing.isPresent() && resumeFromId != null
                ? existing.get().getStartedAt()
                : Instant.now().toString()
        );
        progress.setUpdatedAt(Instant.now().toString());
        progressService.upsert(progress);

        PipelineConfig config = new PipelineConfig();
        config.setJob(job);
        config.setInstanceUrl(instanceUrl);
        config.setAccessToken(accessToken);
        config.setBatchSize(batchSize);
        config.setThreads(threads);
        config.setResumeFromId(resumeFromId);
        config.setInitialProcessed(initialProcessed);

        pipelineEngine.start(config, this::broadcast);
    }

    private void handleRetry(WebSocketSession session, JsonNode msg) {
        if (pipelineEngine.isRunning()) {
            send(session, new PipelineEvent("ERROR").with("message", "Pipeline is already running"));
            return;
        }

        String jobId       = msg.path("jobId").asText();
        String instanceUrl = msg.path("instanceUrl").asText();
        String accessToken = msg.path("accessToken").asText();
        int threads        = msg.path("threads").asInt(5);
        if (maxWorkers > 0 && threads > maxWorkers) threads = maxWorkers;

        if (instanceUrl.isBlank() || accessToken.isBlank()) {
            send(session, new PipelineEvent("ERROR").with("message", "Instance URL and access token are required for retry"));
            return;
        }

        List<String> retryIds = errorService.getErrorRecordIds(jobId, instanceUrl);
        if (retryIds.isEmpty()) {
            send(session, new PipelineEvent("ERROR").with("message", "No failed records to retry"));
            return;
        }

        Job job;
        try {
            job = jobService.getJobById(jobId);
        } catch (Exception e) {
            send(session, new PipelineEvent("ERROR").with("message", "Job not found: " + jobId));
            return;
        }

        // Merge runtime params and apply query token substitution (same as handleStart)
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
        if (!queryTokens.isEmpty() && job.getQuery() != null) {
            String query = job.getQuery();
            for (Map.Entry<String, String> token : queryTokens.entrySet()) {
                query = query.replace("{" + token.getKey() + "}", token.getValue());
            }
            job.setQuery(query);
        }

        // Clear the errors we're about to retry so new failures are fresh
        errorService.clearErrors(jobId, instanceUrl);

        PipelineConfig config = new PipelineConfig();
        config.setJob(job);
        config.setInstanceUrl(instanceUrl);
        config.setAccessToken(accessToken);
        config.setBatchSize(retryIds.size());
        config.setThreads(threads);
        config.setRetryIds(retryIds);

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
