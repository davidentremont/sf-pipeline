package com.sfpipeline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfpipeline.model.Job;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
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
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:jobs/*.json");
            for (Resource resource : resources) {
                try {
                    Job job = objectMapper.readValue(resource.getInputStream(), Job.class);
                    if (job.getId() == null) {
                        job.setId(resource.getFilename().replace(".json", ""));
                    }
                    boolean alreadyLoaded = jobs.stream().anyMatch(j -> j.getId().equals(job.getId()));
                    if (!alreadyLoaded) jobs.add(job);
                } catch (Exception e) {
                    System.err.println("Failed to load classpath job " + resource.getFilename() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load classpath jobs: " + e.getMessage());
        }
    }
}
