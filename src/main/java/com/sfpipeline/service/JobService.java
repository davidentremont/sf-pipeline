package com.sfpipeline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfpipeline.model.Job;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobService {

    @Value("${jobs.directory:jobs}")
    private String jobsDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<Job> cachedJobs;

    @PostConstruct
    public void init() {
        reload();
    }

    public List<Job> getJobs() {
        return cachedJobs;
    }

    public Job getJobById(String id) {
        Job template = cachedJobs.stream()
                .filter(j -> id.equals(j.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(template), Job.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to copy job: " + id, e);
        }
    }

    public void reload() {
        List<Job> jobs = new ArrayList<>();
        Path dir = Path.of(jobsDirectory);
        if (Files.exists(dir)) {
            try {
                Files.list(dir)
                        .filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> {
                            try {
                                Job job = objectMapper.readValue(p.toFile(), Job.class);
                                if (job.getId() == null) {
                                    String filename = p.getFileName().toString();
                                    job.setId(filename.replace(".json", ""));
                                }
                                jobs.add(job);
                            } catch (Exception e) {
                                System.err.println("Failed to load job from " + p + ": " + e.getMessage());
                            }
                        });
            } catch (Exception e) {
                System.err.println("Failed to read jobs directory: " + e.getMessage());
            }
        }
        // Also load from classpath resources/jobs as fallback
        loadClasspathJobs(jobs);
        this.cachedJobs = jobs;
        System.out.println("Loaded " + jobs.size() + " job(s)");
    }

    private void loadClasspathJobs(List<Job> jobs) {
        try {
            var resource = getClass().getClassLoader().getResources("jobs");
            while (resource.hasMoreElements()) {
                var url = resource.nextElement();
                File dir = new File(url.toURI());
                if (dir.isDirectory()) {
                    for (File f : dir.listFiles((d, n) -> n.endsWith(".json"))) {
                        try {
                            Job job = objectMapper.readValue(f, Job.class);
                            if (job.getId() == null) {
                                job.setId(f.getName().replace(".json", ""));
                            }
                            boolean alreadyLoaded = jobs.stream().anyMatch(j -> j.getId().equals(job.getId()));
                            if (!alreadyLoaded) jobs.add(job);
                        } catch (Exception e) {
                            System.err.println("Failed to load classpath job " + f.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Classpath loading is best-effort
        }
    }
}
