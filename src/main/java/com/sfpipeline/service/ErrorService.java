package com.sfpipeline.service;

import com.sfpipeline.model.PluginError;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ErrorService {

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
                    CREATE TABLE IF NOT EXISTS plugin_errors (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        job_id        TEXT    NOT NULL,
                        instance_url  TEXT    NOT NULL,
                        record_id     TEXT    NOT NULL,
                        plugin_name   TEXT    NOT NULL,
                        error_message TEXT    NOT NULL,
                        occurred_at   TEXT    NOT NULL
                    )
                """);
                st.execute("CREATE INDEX IF NOT EXISTS idx_plugin_errors_job ON plugin_errors(job_id, instance_url)");
            }
        } catch (Exception e) {
            System.err.println("ErrorService init failed: " + e.getMessage());
        }
    }

    public synchronized void recordError(String jobId, String instanceUrl,
                                          String recordId, String pluginName, String message) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO plugin_errors (job_id, instance_url, record_id, plugin_name, error_message, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, jobId);
            ps.setString(2, instanceUrl);
            ps.setString(3, recordId);
            ps.setString(4, pluginName);
            ps.setString(5, message);
            ps.setString(6, Instant.now().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("ErrorService.recordError failed: " + e.getMessage());
        }
    }

    public synchronized List<PluginError> getErrors(String jobId, String instanceUrl) {
        List<PluginError> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT * FROM plugin_errors WHERE job_id = ? AND instance_url = ?
                ORDER BY occurred_at DESC
            """)) {
            ps.setString(1, jobId);
            ps.setString(2, instanceUrl);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(fromRow(rs));
            }
        } catch (Exception e) {
            System.err.println("ErrorService.getErrors failed: " + e.getMessage());
        }
        return result;
    }

    public synchronized List<String> getErrorRecordIds(String jobId, String instanceUrl) {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT DISTINCT record_id FROM plugin_errors WHERE job_id = ? AND instance_url = ?
            """)) {
            ps.setString(1, jobId);
            ps.setString(2, instanceUrl);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("record_id"));
            }
        } catch (Exception e) {
            System.err.println("ErrorService.getErrorRecordIds failed: " + e.getMessage());
        }
        return ids;
    }

    public synchronized void clearErrors(String jobId, String instanceUrl) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM plugin_errors WHERE job_id = ? AND instance_url = ?")) {
            ps.setString(1, jobId);
            ps.setString(2, instanceUrl);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("ErrorService.clearErrors failed: " + e.getMessage());
        }
    }

    private PluginError fromRow(ResultSet rs) throws SQLException {
        PluginError e = new PluginError();
        e.setId(rs.getLong("id"));
        e.setJobId(rs.getString("job_id"));
        e.setInstanceUrl(rs.getString("instance_url"));
        e.setRecordId(rs.getString("record_id"));
        e.setPluginName(rs.getString("plugin_name"));
        e.setErrorMessage(rs.getString("error_message"));
        e.setOccurredAt(rs.getString("occurred_at"));
        return e;
    }
}
