package com.sfpipeline.pipeline;

import com.sfpipeline.model.Job;
import com.sfpipeline.model.PipelineConfig;
import com.sfpipeline.model.PipelineEvent;
import com.sfpipeline.plugin.Plugin;
import com.sfpipeline.plugin.PluginContext;
import com.sfpipeline.plugin.PluginRegistry;
import com.sfpipeline.service.SfdxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.IntStream;

@Service
public class PipelineEngine {

    @Autowired
    private QueryEngine queryEngine;

    @Autowired
    private PluginRegistry pluginRegistry;

    @Autowired
    private SfdxService sfdxService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private volatile Consumer<PipelineEvent> eventEmitter;
    private volatile ExecutorService workerPool;

    public boolean isRunning() {
        return running.get();
    }

    public void start(PipelineConfig config, Consumer<PipelineEvent> emitter) {
        if (!running.compareAndSet(false, true)) {
            emitter.accept(new PipelineEvent("ERROR").with("message", "Pipeline is already running"));
            return;
        }

        this.eventEmitter = emitter;
        this.totalProcessed.set(0);

        Thread pipelineThread = new Thread(() -> runPipeline(config), "pipeline-main");
        pipelineThread.setDaemon(true);
        pipelineThread.start();
    }

    public void stop() {
        running.set(false);
        emit(new PipelineEvent("STOPPING"));
        if (workerPool != null) {
            workerPool.shutdownNow();
        }
    }

    private void runPipeline(PipelineConfig config) {
        Job job = config.getJob();
        String org = config.getOrg();
        int batchSize = config.getBatchSize();
        int threads = config.getThreads();

        List<Plugin> plugins = pluginRegistry.getPlugins(job.getPlugins());

        emit(new PipelineEvent("STARTED")
                .with("job", job.getName())
                .with("org", org)
                .with("batchSize", batchSize)
                .with("threads", threads)
                .with("plugins", job.getPlugins()));

        workerPool = Executors.newFixedThreadPool(threads);
        String lastId = null;
        int batchNum = 0;

        try {
            while (running.get()) {
                batchNum++;
                String query = queryEngine.buildQuery(job.getQuery(), lastId, batchSize);

                emit(new PipelineEvent("QUERYING")
                        .with("batch", batchNum)
                        .with("query", query));

                List<Map<String, Object>> records;
                try {
                    records = queryEngine.query(job.getQuery(), lastId, batchSize, org);
                } catch (Exception e) {
                    emit(new PipelineEvent("ERROR").with("message", "Query failed: " + e.getMessage()));
                    break;
                }

                if (records.isEmpty()) {
                    emit(new PipelineEvent("COMPLETE")
                            .with("totalProcessed", totalProcessed.get())
                            .with("message", "No more records"));
                    break;
                }

                lastId = (String) records.get(records.size() - 1).get("Id");

                emit(new PipelineEvent("QUERY_COMPLETE")
                        .with("batch", batchNum)
                        .with("count", records.size())
                        .with("firstId", records.get(0).get("Id"))
                        .with("lastId", lastId));

                // Chunk records across workers
                List<List<Map<String, Object>>> chunks = chunkList(records, threads);
                int actualWorkers = chunks.size();

                // Initialize worker status display
                List<Map<String, Object>> workerInit = new ArrayList<>();
                for (int i = 0; i < actualWorkers; i++) {
                    workerInit.add(Map.of(
                            "id", i + 1,
                            "status", "waiting",
                            "records", chunks.get(i).size()));
                }
                emit(new PipelineEvent("WORKERS_INIT").with("workers", workerInit));

                // Run all workers in parallel and wait
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int i = 0; i < actualWorkers; i++) {
                    final int workerId = i + 1;
                    final List<Map<String, Object>> chunk = chunks.get(i);
                    futures.add(CompletableFuture.runAsync(
                            () -> runWorker(workerId, chunk, plugins, config),
                            workerPool));
                }

                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (CancellationException e) {
                    break;
                }

                totalProcessed.addAndGet(records.size());
                emit(new PipelineEvent("BATCH_COMPLETE")
                        .with("batch", batchNum)
                        .with("totalProcessed", totalProcessed.get()));

                if (records.size() < batchSize) {
                    emit(new PipelineEvent("COMPLETE")
                            .with("totalProcessed", totalProcessed.get())
                            .with("message", "All records processed"));
                    break;
                }
            }
        } finally {
            running.set(false);
            workerPool.shutdown();
            emit(new PipelineEvent("STOPPED").with("totalProcessed", totalProcessed.get()));
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

            PluginContext ctx = new PluginContext(
                    workerId, config.getOrg(), config.getJob(), sfdxService,
                    msg -> emit(new PipelineEvent("WORKER_LOG")
                            .with("workerId", workerId)
                            .with("message", msg)));
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

        emit(new PipelineEvent("WORKER_DONE")
                .with("workerId", workerId)
                .with("count", records.size()));
    }

    private static <T> List<List<T>> chunkList(List<T> list, int numChunks) {
        List<List<T>> chunks = new ArrayList<>();
        int total = list.size();
        int chunkSize = (int) Math.ceil((double) total / numChunks);
        for (int i = 0; i < total; i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, total)));
        }
        return chunks;
    }

    private void emit(PipelineEvent event) {
        Consumer<PipelineEvent> emitter = this.eventEmitter;
        if (emitter != null) {
            try {
                emitter.accept(event);
            } catch (Exception ignored) {}
        }
    }
}
