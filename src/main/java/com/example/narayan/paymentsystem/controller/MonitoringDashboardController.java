package com.example.narayan.paymentsystem.controller;

import com.example.narayan.paymentsystem.service.monitoring.QueueMetricsService;
import com.example.narayan.paymentsystem.service.monitoring.AlertingService;
import com.example.narayan.paymentsystem.queue.failure.FailureTrackingService;
import com.example.narayan.paymentsystem.worker.WorkerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringDashboardController {

    @Autowired
    private QueueMetricsService queueMetricsService;

    @Autowired
    private FailureTrackingService failureTrackingService;

    @Autowired
    private WorkerManager workerManager;

    @Autowired
    private AlertingService alertingService;

    /**
     * Comprehensive monitoring dashboard
     * GET /api/v1/monitoring/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getMonitoringDashboard() {
        Map<String, Object> dashboard = new HashMap<>();

        try {
            // System overview
            Map<String, Object> overview = new HashMap<>();
            QueueMetricsService.QueueMetrics metrics = queueMetricsService.getQueueMetrics();

            overview.put("current_queue_size", metrics.currentQueueSize);
            overview.put("dead_letter_queue_size", metrics.deadLetterQueueSize);
            overview.put("active_workers", metrics.activeWorkers);
            overview.put("total_workers", metrics.totalWorkers);
            overview.put("system_health", determineSystemHealth(metrics));
            overview.put("last_updated", LocalDateTime.now());

            dashboard.put("overview", overview);

            // Performance metrics
            QueueMetricsService.ProcessingRateMetrics rates =
                    queueMetricsService.getProcessingRateMetrics();

            Map<String, Object> performance = new HashMap<>();
            performance.put("jobs_per_minute", rates.jobsPerMinute);
            performance.put("success_rate_percentage", rates.successRate * 100);
            performance.put("current_throughput", rates.currentThroughput);
            performance.put("peak_throughput", rates.peakThroughput);
            performance.put("average_processing_time", metrics.averageProcessingTimeMs);

            dashboard.put("performance", performance);

            // Worker status
            Map<String, Object> workerStatus = new HashMap<>();
            workerStatus.put("total_workers", workerManager.getWorkerCount());
            workerStatus.put("active_workers", workerManager.getActiveWorkerCount());
            workerStatus.put("worker_details", workerManager.getAllWorkerStats());
            workerStatus.put("overall_stats", workerManager.getOverallStats());

            dashboard.put("workers", workerStatus);

            // Failure analysis
            Map<String, Object> failures = new HashMap<>();
            failures.put("failure_statistics", failureTrackingService.getFailureStatistics());
            failures.put("recent_failures", failureTrackingService.getRecentFailures(10));

            dashboard.put("failures", failures);

            // Queue status by type (estimated)
            Map<String, Object> queuesByType = new HashMap<>();
            queuesByType.put("payment_processing", queueMetricsService.getQueueSizeByType("PAYMENT_PROCESSING"));
            queuesByType.put("notification", queueMetricsService.getQueueSizeByType("NOTIFICATION"));
            queuesByType.put("audit", queueMetricsService.getQueueSizeByType("AUDIT"));
            queuesByType.put("webhook", queueMetricsService.getQueueSizeByType("WEBHOOK"));

            dashboard.put("queues_by_type", queuesByType);

            // Alerting status
            Map<String, Object> alerting = new HashMap<>();
            alerting.put("alerting_enabled", alertingService.isAlertingEnabled());
            alerting.put("alert_status", alertingService.getAlertStatus());

            dashboard.put("alerting", alerting);

            return ResponseEntity.ok(dashboard);

        }
        catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to fetch monitoring data");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get current alerting status and active alerts
     * GET /api/v1/monitoring/alerts
     */
    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts() {
        try {
            Map<String, Object> alertData = alertingService.getAlertStatus();
            return ResponseEntity.ok(alertData);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get alert status");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Enable/disable alerting
     * POST /api/v1/monitoring/alerts/toggle
     */
    @PostMapping("/alerts/toggle")
    public ResponseEntity<Map<String, Object>> toggleAlerting(@RequestParam boolean enabled) {
        try {
            alertingService.setAlertingEnabled(enabled);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Alerting " + (enabled ? "enabled" : "disabled"));
            response.put("alerting_enabled", enabled);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to toggle alerting");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Clear all active alerts
     * POST /api/v1/monitoring/alerts/clear
     */
    @PostMapping("/alerts/clear")
    public ResponseEntity<Map<String, String>> clearAlerts() {
        try {
            alertingService.clearAllAlerts();

            Map<String, String> response = new HashMap<>();
            response.put("message", "All alerts cleared");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to clear alerts");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Test alert generation
     * POST /api/v1/monitoring/alerts/test
     */
    @PostMapping("/alerts/test")
    public ResponseEntity<Map<String, Object>> testAlert(
            @RequestParam(defaultValue = "WARNING") String level,
            @RequestParam(defaultValue = "Test alert from API") String message) {
        try {
            AlertingService.AlertLevel alertLevel = AlertingService.AlertLevel.valueOf(level.toUpperCase());
            alertingService.testAlert(alertLevel, message);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Test alert generated");
            response.put("level", level);
            response.put("test_message", message);
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate test alert");
            error.put("message", e.getMessage());
            error.put("timestamp", LocalDateTime.now());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get historical performance data
     * GET /api/v1/monitoring/performance-history
     */
    @GetMapping("/performance-history")
    public ResponseEntity<Map<String, Object>> getPerformanceHistory(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "1") int intervalHours) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("historical_metrics", queueMetricsService.getHistoricalMetrics(hours, intervalHours));
            response.put("period_hours", hours);
            response.put("interval_hours", intervalHours);
            response.put("generated_at", LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to fetch historical data");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Force worker pool scaling
     * POST /api/v1/monitoring/scale-workers
     */
    @PostMapping("/scale-workers")
    public ResponseEntity<Map<String, Object>> scaleWorkers(
            @RequestParam int additionalWorkers) {
        try {
            if (additionalWorkers < 1 || additionalWorkers > 20) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid additional worker count. Must be between 1 and 20");
                return ResponseEntity.badRequest().body(error);
            }

            if (!workerManager.isStarted()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "WorkerManager not started");
                return ResponseEntity.badRequest().body(error);
            }

            workerManager.addMoreWorkers(additionalWorkers);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Workers scaling initiated");
            response.put("additional_workers", additionalWorkers);
            response.put("total_workers", workerManager.getWorkerCount());
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to scale workers");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Force stats reporting
     * POST /api/v1/monitoring/force-stats
     */
    @PostMapping("/force-stats")
    public ResponseEntity<Map<String, String>> forceStatsReport() {
        try {
            workerManager.printWorkerStats();

            Map<String, String> response = new HashMap<>();
            response.put("message", "Stats report generated");
            response.put("output", "Check console/logs for detailed stats");
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate stats report");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * System health check with detailed status
     * GET /api/v1/monitoring/system-health
     */
    @GetMapping("/system-health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        try {
            QueueMetricsService.QueueMetrics metrics = queueMetricsService.getQueueMetrics();
            QueueMetricsService.WorkerHealthMetrics workerHealth = queueMetricsService.getWorkerHealthMetrics();

            String overallHealth = determineSystemHealth(metrics);

            Map<String, Object> healthStatus = new HashMap<>();
            healthStatus.put("overall_status", overallHealth);
            healthStatus.put("timestamp", LocalDateTime.now());

            // Queue health
            Map<String, Object> queueHealth = new HashMap<>();
            queueHealth.put("queue_size", metrics.currentQueueSize);
            queueHealth.put("dead_letter_size", metrics.deadLetterQueueSize);
            queueHealth.put("status", metrics.currentQueueSize < 1000 ? "HEALTHY" :
                    metrics.currentQueueSize < 5000 ? "WARNING" : "CRITICAL");
            healthStatus.put("queue_health", queueHealth);

            // Worker health
            Map<String, Object> workerHealthMap = new HashMap<>();
            workerHealthMap.put("total_workers", workerHealth.totalWorkers);
            workerHealthMap.put("active_workers", workerHealth.activeWorkers);
            workerHealthMap.put("worker_utilization", workerHealth.workerUtilization);
            workerHealthMap.put("status", workerHealth.overallHealth);
            healthStatus.put("worker_health", workerHealthMap);

            // Performance health
            QueueMetricsService.ProcessingRateMetrics rates = queueMetricsService.getProcessingRateMetrics();
            Map<String, Object> performanceHealth = new HashMap<>();
            performanceHealth.put("success_rate", rates.successRate);
            performanceHealth.put("throughput", rates.currentThroughput);
            performanceHealth.put("status", rates.successRate > 0.95 ? "HEALTHY" :
                    rates.successRate > 0.90 ? "WARNING" : "CRITICAL");
            healthStatus.put("performance_health", performanceHealth);

            // Return appropriate HTTP status
            if ("CRITICAL".equals(overallHealth)) {
                return ResponseEntity.status(503).body(healthStatus);
            } else if ("WARNING".equals(overallHealth)) {
                return ResponseEntity.status(200).body(healthStatus);
            } else {
                return ResponseEntity.ok(healthStatus);
            }

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("overall_status", "CRITICAL");
            error.put("error", "Health check failed");
            error.put("message", e.getMessage());
            error.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(503).body(error);
        }
    }

    /**
     * Get system summary for quick overview
     * GET /api/v1/monitoring/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSystemSummary() {
        try {
            QueueMetricsService.QueueMetrics metrics = queueMetricsService.getQueueMetrics();

            Map<String, Object> summary = new HashMap<>();
            summary.put("queue_size", metrics.currentQueueSize);
            summary.put("dead_letter_size", metrics.deadLetterQueueSize);
            summary.put("active_workers", metrics.activeWorkers);
            summary.put("total_workers", metrics.totalWorkers);
            summary.put("total_processed", metrics.totalJobsProcessed);
            summary.put("total_failed", metrics.totalJobsFailed);
            summary.put("system_health", determineSystemHealth(metrics));
            summary.put("worker_manager_started", workerManager.isStarted());
            summary.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get system summary");
            error.put("message", e.getMessage());
            error.put("timestamp", LocalDateTime.now());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Determine system health based on metrics
     */
    private String determineSystemHealth(QueueMetricsService.QueueMetrics metrics) {
        // Critical conditions
        if (metrics.activeWorkers == 0 && metrics.currentQueueSize > 0) {
            return "CRITICAL";
        }

        if (metrics.currentQueueSize > 10000) {
            return "CRITICAL";
        }

        if (metrics.deadLetterQueueSize > 1000) {
            return "CRITICAL";
        }

        // Warning conditions
        if (metrics.currentQueueSize > 1000) {
            return "WARNING";
        }

        if (metrics.deadLetterQueueSize > 100) {
            return "WARNING";
        }

        if (metrics.activeWorkers < metrics.totalWorkers * 0.5) {
            return "WARNING";
        }

        if (metrics.averageProcessingTimeMs > 30000) { // 30 seconds
            return "WARNING";
        }

        return "HEALTHY";
    }
}