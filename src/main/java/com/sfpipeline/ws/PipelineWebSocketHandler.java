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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class PipelineWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private PipelineEngine pipelineEngine;

    @Autowired
    private JobService jobService;

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
        String type = msg.path("type").asText();

        switch (type) {
            case "START" -> handleStart(session, msg);
            case "STOP"  -> pipelineEngine.stop();
            default      -> send(session, new PipelineEvent("ERROR").with("message", "Unknown command: " + type));
        }
    }

    private void handleStart(WebSocketSession session, JsonNode msg) throws Exception {
        if (pipelineEngine.isRunning()) {
            send(session, new PipelineEvent("ERROR").with("message", "Pipeline is already running"));
            return;
        }

        String jobId    = msg.path("jobId").asText();
        String org      = msg.path("org").asText();
        int batchSize   = msg.path("batchSize").asInt(1000);
        int threads     = msg.path("threads").asInt(5);

        if (org.isBlank()) {
            send(session, new PipelineEvent("ERROR").with("message", "Salesforce org username is required"));
            return;
        }

        Job job;
        try {
            job = jobService.getJobById(jobId);
        } catch (Exception e) {
            send(session, new PipelineEvent("ERROR").with("message", "Job not found: " + jobId));
            return;
        }

        PipelineConfig config = new PipelineConfig();
        config.setJob(job);
        config.setOrg(org);
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
            String json = objectMapper.writeValueAsString(event);
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        try {
                            session.sendMessage(msg);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to broadcast event: " + e.getMessage());
        }
    }

    private void send(WebSocketSession session, PipelineEvent event) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            System.err.println("Failed to send to session " + session.getId() + ": " + e.getMessage());
        }
    }
}
