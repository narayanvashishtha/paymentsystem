package com.example.narayan.paymentsystem.actuator;

import com.example.narayan.paymentsystem.service.monitoring.QueueMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Actuator endpoint for worker health monitoring
 * Accessible at /actuator/worker-health
 */
@Component
@Endpoint(id = "worker-health")
public class WorkerHealthEndpoint implements HealthIndicator {

    @Autowired
    private QueueMetricsService queueMetricsService;

    /**
     * Main worker health endpoint
     * GET /actuator/worker-health
     */
    @ReadOperation
    public Map<String, Object> workerHealth() {
        Map<String, Object> health = new HashMap<>();

        try {
            QueueMetricsService.WorkerHealthMetrics healthMetrics =
                    queueMetricsService.getWorkerHealthMetrics();

            // Overall health summary
            Map<String, Object> summary = new HashMap<>();
            summary.put("overall_health", healthMetrics.overallHealth);
            summary.put("total_workers", healthMetrics.totalWorkers);
            summary.put("active_workers", healthMetrics.activeWorkers);
            summary.put("healthy_workers", healthMetrics.healthyWorkers);
            summary.put("worker_utilization", String.format("%.1f%%", healthMetrics.workerUtilization * 100));
            summary.put("last_health_check", healthMetrics.lastHealthCheck);

            health.put("summary", summary);

            // Individual worker details
            health.put("worker_details", healthMetrics.workerDetails);

            // Health indicators
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("workers_available", healthMetrics.activeWorkers > 0);
            indicators.put("all_workers_healthy",
                    healthMetrics.activeWorkers == healthMetrics.totalWorkers);
            indicators.put("sufficient_capacity", healthMetrics.workerUtilization < 0.9);

            health.put("indicators", indicators);

            // Determine overall status
            String status = determineOverallStatus(healthMetrics);
            health.put("status", status);
            health.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now());
        }

        return health;
    }

    /**
     * Individual worker details endpoint
     * GET /actuator/worker-health/details
     */
    @ReadOperation
    public Map<String, Object> workerDetails(@Selector String selector) {
        if (!"details".equals(selector)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Unknown selector: " + selector);
            error.put("available_selectors", new String[]{"details"});
            return error;
        }

        Map<String, Object> details = new HashMap<>();

        try {
            QueueMetricsService.WorkerHealthMetrics healthMetrics =
                    queueMetricsService.getWorkerHealthMetrics();

            // Detailed worker information
            details.put("workers", healthMetrics.workerDetails);

            // Worker statistics summary
            Map<String, Object> stats = new HashMap<>();
            long totalProcessed = healthMetrics.workerDetails.stream()
                    .mapToLong(w -> w.processedJobs)
                    .sum();
            long totalFailed = healthMetrics.workerDetails.stream()
                    .mapToLong(w -> w.failedJobs)
                    .sum();
            double avgProcessingTime = healthMetrics.workerDetails.stream()
                    .mapToLong(w -> w.averageProcessingTime)
                    .average()
                    .orElse(0.0);

            stats.put("total_jobs_processed_by_all_workers", totalProcessed);
            stats.put("total_jobs_failed_by_all_workers", totalFailed);
            stats.put("average_processing_time_across_workers", avgProcessingTime);
            stats.put("success_rate_across_workers",
                    totalProcessed + totalFailed > 0 ?
                            (double) totalProcessed / (totalProcessed + totalFailed) : 0.0);

            details.put("aggregated_stats", stats);
            details.put("status", "healthy");
            details.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            details.put("status", "error");
            details.put("error", e.getMessage());
            details.put("timestamp", LocalDateTime.now());
        }

        return details;
    }

    /**
     * Spring Boot Health Indicator implementation
     * This integrates with /actuator/health
     */
    @Override
    public Health health() {
        try {
            QueueMetricsService.WorkerHealthMetrics healthMetrics =
                    queueMetricsService.getWorkerHealthMetrics();

            Map<String, Object> details = new HashMap<>();
            details.put("total_workers", healthMetrics.totalWorkers);
            details.put("active_workers", healthMetrics.activeWorkers);
            details.put("healthy_workers", healthMetrics.healthyWorkers);
            details.put("overall_health", healthMetrics.overallHealth);

            // Determine health status
            if ("HEALTHY".equals(healthMetrics.overallHealth)) {
                return Health.up()
                        .withDetails(details)
                        .build();
            } else if ("WARNING".equals(healthMetrics.overallHealth)) {
                return Health.status("WARNING")
                        .withDetails(details)
                        .build();
            } else {
                return Health.down()
                        .withDetails(details)
                        .build();
            }

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private String determineOverallStatus(QueueMetricsService.WorkerHealthMetrics healthMetrics) {
        if (healthMetrics.totalWorkers == 0) {
            return "DOWN"; // No workers configured
        }

        if (healthMetrics.activeWorkers == 0) {
            return "DOWN"; // No workers active
        }

        if (healthMetrics.activeWorkers == healthMetrics.totalWorkers) {
            return "UP"; // All workers active
        }

        if (healthMetrics.activeWorkers >= healthMetrics.totalWorkers * 0.8) {
            return "WARNING"; // Most workers active
        }

        return "DOWN"; // Too many workers inactive
    }
}