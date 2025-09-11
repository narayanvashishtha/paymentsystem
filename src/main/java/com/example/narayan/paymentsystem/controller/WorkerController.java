package com.example.narayan.paymentsystem.controller;

import com.example.narayan.paymentsystem.worker.JobWorker;
import com.example.narayan.paymentsystem.worker.WorkerManager;
import com.example.narayan.paymentsystem.queue.JobQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workers")
public class WorkerController {

    @Autowired
    private WorkerManager workerManager;

    @Autowired
    private JobQueue jobQueue;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getWorkerStats() {
        Map<String, Object> stats = new HashMap<>();

        // Overall stats
        stats.put("overall", workerManager.getOverallStats());
        stats.put("manager_started", workerManager.isStarted());
        stats.put("total_workers", workerManager.getWorkerCount());
        stats.put("active_workers", workerManager.getActiveWorkerCount());
        stats.put("queue_size", jobQueue.size());

        // Individual worker stats
        List<JobWorker.WorkerStats> workerStats = workerManager.getAllWorkerStats();
        stats.put("workers", workerStats);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getWorkerHealth() {
        Map<String, Object> health = new HashMap<>();

        boolean isHealthy = workerManager.isStarted() &&
                workerManager.getActiveWorkerCount() > 0;

        health.put("status", isHealthy ? "healthy" : "unhealthy");
        health.put("active_workers", workerManager.getActiveWorkerCount());
        health.put("expected_workers", workerManager.getWorkerCount());
        health.put("queue_size", jobQueue.size());

        if (isHealthy) {
            return ResponseEntity.ok(health);
        } else {
            return ResponseEntity.status(503).body(health);
        }
    }

    @PostMapping("/scale")
    public ResponseEntity<Map<String, String>> scaleWorkers(@RequestParam int additionalWorkers) {
        Map<String, String> response = new HashMap<>();

        if (additionalWorkers <= 0) {
            response.put("error", "additionalWorkers must be positive");
            return ResponseEntity.badRequest().body(response);
        }

        if (!workerManager.isStarted()) {
            response.put("error", "WorkerManager not started");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            workerManager.addMoreWorkers(additionalWorkers);
            response.put("message", "Added " + additionalWorkers + " workers");
            response.put("total_workers", String.valueOf(workerManager.getWorkerCount()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Failed to add workers: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/force-stats")
    public ResponseEntity<String> forceStatsReport() {
        workerManager.printWorkerStats();
        return ResponseEntity.ok("Stats printed to console");
    }
}