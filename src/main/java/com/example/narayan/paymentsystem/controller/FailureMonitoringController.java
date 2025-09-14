package com.example.narayan.paymentsystem.controller;

import com.example.narayan.paymentsystem.queue.failure.FailureAnalysis;
import com.example.narayan.paymentsystem.queue.failure.FailureTrackingService;
import com.example.narayan.paymentsystem.queue.DeadLetterQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitoring")
public class FailureMonitoringController {

    @Autowired
    private FailureTrackingService failureTrackingService;

    @Autowired
    private DeadLetterQueue deadLetterQueue;

    /**
     * Get comprehensive failure statistics
     */
    @GetMapping("/failures/stats")
    public ResponseEntity<Map<String, Object>> getFailureStats() {
        Map<String, Object> stats = failureTrackingService.getFailureStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get recent failure details for debugging
     */
    @GetMapping("/failures/recent")
    public ResponseEntity<List<FailureAnalysis>> getRecentFailures(
            @RequestParam(defaultValue = "20") int limit) {

        if (limit > 100) limit = 100; // Prevent excessive data

        List<FailureAnalysis> recentFailures = failureTrackingService.getRecentFailures(limit);
        return ResponseEntity.ok(recentFailures);
    }

    /**
     * Generate a detailed failure analysis report
     */
    @GetMapping("/failures/report")
    public ResponseEntity<Map<String, String>> getFailureReport() {
        String report = failureTrackingService.generateFailureReport();
        Map<String, String> response = new HashMap<>();
        response.put("report", report);
        response.put("format", "text");
        return ResponseEntity.ok(response);
    }

    /**
     * Get dead letter queue information with failure analysis
     */
    @GetMapping("/dead-letter")
    public ResponseEntity<Map<String, Object>> getDeadLetterInfo() {
        Map<String, Object> info = new HashMap<>();

        List<String> deadJobs = deadLetterQueue.getDeadLetterJobs();
        long count = deadLetterQueue.getDeadLetterCount();

        info.put("count", count);
        info.put("jobs", deadJobs);

        return ResponseEntity.ok(info);
    }

    /**
     * Get metadata for a specific dead letter job
     */
    @GetMapping("/dead-letter/{jobId}")
    public ResponseEntity<Map<String, Object>> getDeadLetterJobMetadata(@PathVariable String jobId) {
        String metadata = deadLetterQueue.getJobMetadata(jobId);
        Map<String, Object> response = new HashMap<>();

        if (metadata != null) {
            response.put("jobId", jobId);
            response.put("metadata", metadata);
            response.put("found", true);
        } else {
            response.put("jobId", jobId);
            response.put("found", false);
            response.put("message", "Job metadata not found");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Clear failure statistics (for testing/cleanup)
     */
    @DeleteMapping("/failures/stats")
    public ResponseEntity<Map<String, String>> clearFailureStats() {
        failureTrackingService.clearFailureStats();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Failure statistics cleared");
        return ResponseEntity.ok(response);
    }

    /**
     * Clear dead letter queue (use with caution)
     */
    @DeleteMapping("/dead-letter")
    public ResponseEntity<Map<String, String>> clearDeadLetterQueue() {
        deadLetterQueue.clearDeadLetterQueue();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Dead letter queue cleared");
        return ResponseEntity.ok(response);
    }

    /**
     * Health check for monitoring system
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getMonitoringHealth() {
        Map<String, Object> health = new HashMap<>();

        try {
            // Test if we can get stats
            Map<String, Object> stats = failureTrackingService.getFailureStatistics();
            long deadLetterCount = deadLetterQueue.getDeadLetterCount();

            health.put("status", "healthy");
            health.put("failure_tracking", "operational");
            health.put("dead_letter_queue", "operational");
            health.put("dead_letter_count", deadLetterCount);

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }
}