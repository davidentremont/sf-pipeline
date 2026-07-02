package com.sfpipeline.pipeline;

import com.sfpipeline.model.Job;
import com.sfpipeline.model.PipelineConfig;
import com.sfpipeline.model.PipelineEvent;
import com.sfpipeline.plugin.Plugin;
import com.sfpipeline.plugin.PluginContext;
import com.sfpipeline.plugin.PluginRegistry;
import com.sfpipeline.service.ErrorService;
import com.sfpipeline.service.ProgressService;
import com.sfpipeline.service.SalesforceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class PipelineEngine {

    @Autowired
    private QueryEngine queryEngine;

    @Autowired
    private PluginRegistry pluginRegistry;

    @Autowired
    private SalesforceService salesforceService;

    @Autowired
    private ProgressService progressService;

    @Autowired
    private ErrorService errorService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private volatile Consumer<PipelineEvent> eventEmitter;
    private volatile ExecutorService workerPool;

    public boolean isRunning() { return running.get(); }

    public void start(PipelineConfig config, Consumer<PipelineEvent> emitter) {
        if (!running.compareAndSet(false, true)) {
            emitter.accept(new PipelineEvent("ERROR").with("message", "Pipeline is already running"));
            return;
        }
        this.eventEmitter = emitter;
        this.totalProcessed.set(config.getInitialProcessed());
        Thread t = new Thread(() -> runPipeline(config), "pipeline-main");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running.set(false);
        emit(new PipelineEvent("STOPPING"));
        if (workerPool != null) workerPool.shutdownNow();
    }

    private void runPipeline(PipelineConfig config) {
        Job job = config.getJob();
        String instanceUrl = config.getInstanceUrl();
        String accessToken = config.getAccessToken();
        int batchSize = config.getBatchSize();
        int threads = config.getThreads();
        String jobId = job.getId();

        List<Plugin> plugins = pluginRegistry.getPlugins(job.getPlugins());

        List<String> retryIds = config.getRetryIds();
        boolean isRetry = retryIds != null && !retryIds.isEmpty();

        emit(new PipelineEvent("STARTED")
                .with("job", job.getName())
                .with("instanceUrl", instanceUrl)
                .with("batchSize", batchSize)
                .with("threads", threads)
                .with("plugins", job.getPlugins())
                .with("resumeFromId", config.getResumeFromId())
                .with("initialProcessed", config.getInitialProcessed())
                .with("retry", isRetry)
                .with("retryCount", isRetry ? retryIds.size() : 0));

        if (!isRetry) {
            // Fetch total record count for progress tracking (non-fatal if it fails)
            String objectType = extractObjectType(job.getQuery());
            if (objectType != null) {
                long totalCount = fetchTotalCount(job.getQuery(), objectType, instanceUrl, accessToken);
                if (totalCount > 0) {
                    emit(new PipelineEvent("TOTAL_COUNT")
                            .with("objectType", objectType)
                            .with("totalCount", totalCount));
                    progressService.setTotalCount(jobId, instanceUrl, totalCount);
                }
            }
        }

        workerPool = Executors.newCachedThreadPool();
        AtomicInteger workerIdSeq = new AtomicInteger(0);
        List<CompletableFuture<Void>> allFutures = new ArrayList<>();
        boolean completedNormally = false;
        boolean errorOccurred = false;

        try {
            if (isRetry) {
                completedNormally = runRetry(retryIds, job, instanceUrl, accessToken, threads,
                        plugins, config, workerIdSeq, allFutures);
                if (!completedNormally) errorOccurred = true;
            } else {
                String lastId = config.getResumeFromId();
                int batchNum = 0;

                while (running.get()) {
                    batchNum++;
                    String query = queryEngine.buildQuery(job.getQuery(), lastId, batchSize);

                    emit(new PipelineEvent("QUERYING")
                            .with("batch", batchNum)
                            .with("query", query));

                    List<Map<String, Object>> records;
                    try {
                        records = salesforceService.runQuery(query, instanceUrl, accessToken);
                    } catch (Exception e) {
                        emit(new PipelineEvent("ERROR").with("message", "Query failed: " + e.getMessage()));
                        errorOccurred = true;
                        break;
                    }

                    if (records.isEmpty()) {
                        completedNormally = true;
                        break;
                    }

                    lastId = (String) records.get(records.size() - 1).get("Id");

                    emit(new PipelineEvent("QUERY_COMPLETE")
                            .with("batch", batchNum)
                            .with("count", records.size())
                            .with("firstId", records.get(0).get("Id"))
                            .with("lastId", lastId));

                    dispatchWorkers(records, threads, plugins, config, workerIdSeq, allFutures);

                    emit(new PipelineEvent("BATCH_COMPLETE")
                            .with("batch", batchNum)
                            .with("totalProcessed", totalProcessed.get()));

                    progressService.updateBatch(jobId, instanceUrl, lastId, totalProcessed.get(), batchNum);
                }

                // Wait for all in-flight workers across all batches before finishing.
                awaitWorkers(allFutures);
                progressService.updateBatch(jobId, instanceUrl, lastId, totalProcessed.get(), batchNum);
            }

            if (completedNormally) {
                emit(new PipelineEvent("COMPLETE")
                        .with("totalProcessed", totalProcessed.get())
                        .with("message", isRetry ? "Retry complete" : "All records processed"));
            }

        } finally {
            String finalStatus = completedNormally ? "completed" : (errorOccurred ? "error" : "stopped");
            if (!isRetry) {
                progressService.setStatus(jobId, instanceUrl, finalStatus, totalProcessed.get());
            }

            running.set(false);
            workerPool.shutdown();
            emit(new PipelineEvent("STOPPED").with("totalProcessed", totalProcessed.get()));
        }
    }

    private boolean runRetry(List<String> retryIds, Job job, String instanceUrl, String accessToken,
                              int threads, List<Plugin> plugins, PipelineConfig config,
                              AtomicInteger workerIdSeq, List<CompletableFuture<Void>> allFutures) {
        int chunkSize = 200;
        List<Map<String, Object>> allRecords = new ArrayList<>();

        for (int i = 0; i < retryIds.size(); i += chunkSize) {
            List<String> idChunk = retryIds.subList(i, Math.min(i + chunkSize, retryIds.size()));
            String query = buildIdInQuery(job.getQuery(), idChunk);
            emit(new PipelineEvent("QUERYING").with("batch", (i / chunkSize) + 1).with("query", query));
            try {
                List<Map<String, Object>> records = salesforceService.runQuery(query, instanceUrl, accessToken);
                allRecords.addAll(records);
            } catch (Exception e) {
                emit(new PipelineEvent("ERROR").with("message", "Retry query failed: " + e.getMessage()));
                return false;
            }
        }

        if (allRecords.isEmpty()) return true;

        dispatchWorkers(allRecords, threads, plugins, config, workerIdSeq, allFutures);
        awaitWorkers(allFutures);
        return running.get();
    }

    private void dispatchWorkers(List<Map<String, Object>> records, int threads, List<Plugin> plugins,
                                  PipelineConfig config, AtomicInteger workerIdSeq,
                                  List<CompletableFuture<Void>> allFutures) {
        List<List<Map<String, Object>>> chunks = chunkList(records, threads);
        int actualWorkers = chunks.size();

        List<Map<String, Object>> workerInit = new ArrayList<>();
        List<Integer> workerIds = new ArrayList<>();
        for (int i = 0; i < actualWorkers; i++) {
            int wid = workerIdSeq.incrementAndGet();
            workerIds.add(wid);
            workerInit.add(Map.of("id", wid, "status", "waiting", "records", chunks.get(i).size()));
        }
        emit(new PipelineEvent("WORKERS_INIT").with("workers", workerInit));

        for (int i = 0; i < actualWorkers; i++) {
            final int workerId = workerIds.get(i);
            final List<Map<String, Object>> chunk = chunks.get(i);
            allFutures.add(CompletableFuture.runAsync(() ->
                runWorker(workerId, chunk, plugins, config), workerPool));
        }

        allFutures.removeIf(CompletableFuture::isDone);
    }

    private void awaitWorkers(List<CompletableFuture<Void>> allFutures) {
        if (!allFutures.isEmpty()) {
            try {
                CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();
            } catch (CompletionException | CancellationException ignored) {}
        }
    }

    private void runWorker(int workerId, List<Map<String, Object>> records,
                            List<Plugin> plugins, PipelineConfig config) {
        emit(new PipelineEvent("WORKER_START")
                .with("workerId", workerId)
                .with("count", records.size()));

        List<Map<String, Object>> data = records;
        for (Plugin plugin : plugins) {
            if (!running.get()) break;

            emit(new PipelineEvent("WORKER_PLUGIN")
                    .with("workerId", workerId)
                    .with("plugin", plugin.getName()));

            final String pluginName = plugin.getName();
            PluginContext ctx = new PluginContext(
                    workerId, config.getInstanceUrl(), config.getAccessToken(),
                    config.getJob(), salesforceService,
                    msg -> emit(new PipelineEvent("WORKER_LOG")
                            .with("workerId", workerId)
                            .with("message", msg)),
                    (recordId, message) -> {
                        errorService.recordError(config.getJob().getId(), config.getInstanceUrl(),
                                recordId, pluginName, message);
                        emit(new PipelineEvent("RECORD_ERROR")
                                .with("workerId", workerId)
                                .with("recordId", recordId)
                                .with("pluginName", pluginName)
                                .with("errorMessage", message));
                    });
            try {
                data = plugin.execute(data, ctx);
            } catch (Exception e) {
                emit(new PipelineEvent("WORKER_ERROR")
                        .with("workerId", workerId)
                        .with("plugin", plugin.getName())
                        .with("error", e.getMessage()));
                break;
            }
        }

        long updated = totalProcessed.addAndGet(records.size());
        emit(new PipelineEvent("WORKER_DONE")
                .with("workerId", workerId)
                .with("count", records.size())
                .with("totalProcessed", updated));
    }

    private static String buildIdInQuery(String baseQuery, List<String> ids) {
        Matcher m = Pattern.compile("^(SELECT\\s+.+?\\s+FROM\\s+\\w+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(baseQuery.trim());
        if (!m.find()) throw new RuntimeException("Cannot parse query for retry: " + baseQuery);
        String quoted = ids.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
        return m.group(1) + " WHERE Id IN (" + quoted + ")";
    }

    private long fetchTotalCount(String queryTemplate, String objectType, String instanceUrl, String accessToken) {
        String whereClause = extractWhereClause(queryTemplate);
        if (whereClause == null) {
            try {
                return salesforceService.getRecordCount(objectType, instanceUrl, accessToken);
            } catch (Exception e) {
                return 0;
            }
        }
        try {
            String countSoql = "SELECT COUNT() FROM " + objectType + " WHERE " + whereClause;
            return salesforceService.runCountQuery(countSoql, instanceUrl, accessToken);
        } catch (Exception e) {
            try {
                return salesforceService.getRecordCount(objectType, instanceUrl, accessToken);
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    private static String extractObjectType(String query) {
        if (query == null) return null;
        Matcher m = Pattern.compile("\\bFROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE).matcher(query);
        return m.find() ? m.group(1) : null;
    }

    private static String extractWhereClause(String query) {
        if (query == null) return null;
        Matcher m = Pattern.compile(
                "\\bWHERE\\b(.+?)(?:\\bORDER\\b|\\bLIMIT\\b|\\bGROUP\\b|\\bHAVING\\b|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(query);
        return m.find() ? m.group(1).trim() : null;
    }

    private static <T> List<List<T>> chunkList(List<T> list, int numChunks) {
        List<List<T>> chunks = new ArrayList<>();
        int chunkSize = (int) Math.ceil((double) list.size() / numChunks);
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }

    private void emit(PipelineEvent event) {
        Consumer<PipelineEvent> emitter = this.eventEmitter;
        if (emitter != null) {
            try { emitter.accept(event); } catch (Exception ignored) {}
        }
    }
}
