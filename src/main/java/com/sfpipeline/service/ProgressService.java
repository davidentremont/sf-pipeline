package com.sfpipeline.service;

import com.sfpipeline.model.PipelineProgress;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProgressService {

    @Value("${progress.db:data/pipeline.db}")
    private String dbPath;

    private Connection connection;

    @PostConstruct
    public synchronized void init() {
        try {
            File dbFile = new File(dbPath);
            if (dbFile.getParentFile() != null) dbFile.getParentFile().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS pipeline_progress (
                        job_id          TEXT    NOT NULL,
                        instance_url    TEXT    NOT NULL,
                        query           TEXT,
                        last_id         TEXT,
                        total_processed INTEGER DEFAULT 0,
                        batch_num       INTEGER DEFAULT 0,
                        status          TEXT    DEFAULT 'running',
                        started_at      TEXT,
                        updated_at      TEXT,
                        PRIMARY KEY (job_id, instance_url)
                    )
                """);
                // Migrations: add columns added after initial schema
                try { st.execute("ALTER TABLE pipeline_progress ADD COLUMN total_count INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
                try { st.execute("ALTER TABLE pipeline_progress ADD COLUMN finished_at TEXT"); } catch (SQLException ignored) {}
            }
        } catch (Exception e) {
            System.err.println("ProgressService init failed: " + e.getMessage());
        }
    }

    public synchronized Optional<PipelineProgress> get(String jobId, String instanceUrl) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM pipeline_progress WHERE job_id = ? AND instance_url = ?")) {
            ps.setString(1, jobId);
            ps.setString(2, instanceUrl);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromRow(rs));
            }
        } catch (Exception e) {
            System.err.println("ProgressService.get failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    public synchronized void upsert(PipelineProgress p) {
        String sql = """
            INSERT INTO pipeline_progress
                (job_id, instance_url, query, last_id, total_processed, batch_num, total_count, status, started_at, updated_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(job_id, instance_url) DO UPDATE SET
                query           = excluded.query,
                last_id         = excluded.last_id,
                total_processed = excluded.total_processed,
                batch_num       = excluded.batch_num,
                total_count     = excluded.total_count,
                status          = excluded.status,
                started_at      = excluded.started_at,
                updated_at      = excluded.updated_at,
                finished_at     = excluded.finished_at
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, p.getJobId());
            ps.setString(2, p.getInstanceUrl());
            ps.setString(3, p.getQuery());
            ps.setString(4, p.getLastId());
            ps.setLong(5, p.getTotalProcessed());
            ps.setInt(6, p.getBatchNum());
            ps.setLong(7, p.getTotalCount());
            ps.setString(8, p.getStatus());
            ps.setString(9, p.getStartedAt());
            ps.setString(10, p.getUpdatedAt());
            ps.setString(11, p.getFinishedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("ProgressService.upsert failed: " + e.getMessage());
        }
    }

    public synchronized void updateBatch(String jobId, String instanceUrl, String lastId, long totalProcessed, int batchNum) {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE pipeline_progress
                SET last_id = ?, total_processed = ?, batch_num = ?, updated_at = ?
                WHERE job_id = ? AND instance_url = ?
            """)) {
            ps.setString(1, lastId);
            ps.setLong(2, totalProcessed);
            ps.setInt(3, batchNum);
            ps.setString(4, now());
            ps.setString(5, jobId);
            ps.setString(6, instanceUrl);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("ProgressService.updateBatch failed: " + e.getMessage());
        }
    }

    public synchronized void setTotalCount(String jobId, String instanceUrl, long totalCount) {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE pipeline_progress
                SET total_count = ?, updated_at = ?
                WHERE job_id = ? AND instance_url = ?
            """)) {
            ps.setLong(1, totalCount);
            ps.setString(2, now());
            ps.setString(3, jobId);
            ps.setString(4, instanceUrl);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("ProgressService.setTotalCount failed: " + e.getMessage());
        }
    }

    public synchronized void setStatus(String jobId, String instanceUrl, String status, long totalProcessed) {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE pipeline_progress
                SET status = ?, total_processed = ?, updated_at = ?, finished_at = ?
                WHERE job_id = ? AND instance_url = ?
            """)) {
            ps.setString(1, status);
            ps.setLong(2, totalProcessed);
            ps.setString(3, now());
            ps.setString(4, now());
            ps.setString(5, jobId);
            ps.setString(6, instanceUrl);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("ProgressService.setStatus failed: " + e.getMessage());
        }
    }

    public synchronized List<PipelineProgress> getAll() {
        List<PipelineProgress> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM pipeline_progress ORDER BY updated_at DESC")) {
            while (rs.next()) result.add(fromRow(rs));
        } catch (Exception e) {
            System.err.println("ProgressService.getAll failed: " + e.getMessage());
        }
        return result;
    }

    private PipelineProgress fromRow(ResultSet rs) throws SQLException {
        PipelineProgress p = new PipelineProgress();
        p.setJobId(rs.getString("job_id"));
        p.setInstanceUrl(rs.getString("instance_url"));
        p.setQuery(rs.getString("query"));
        p.setLastId(rs.getString("last_id"));
        p.setTotalProcessed(rs.getLong("total_processed"));
        p.setBatchNum(rs.getInt("batch_num"));
        p.setTotalCount(rs.getLong("total_count"));
        p.setStatus(rs.getString("status"));
        p.setStartedAt(rs.getString("started_at"));
        p.setUpdatedAt(rs.getString("updated_at"));
        p.setFinishedAt(rs.getString("finished_at"));
        return p;
    }

    private String now() {
        return Instant.now().toString();
    }
}
