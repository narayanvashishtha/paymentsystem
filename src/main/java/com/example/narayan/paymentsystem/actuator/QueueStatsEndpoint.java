package com.example.narayan.paymentsystem.actuator;

import com.example.narayan.paymentsystem.service.monitoring.QueueMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Actuator endpoint for queue statistics
 * Accessible at /actuator/queue-stats
 */
@Component
@Endpoint(id = "queue-stats")
public class QueueStatsEndpoint {

    @Autowired
    private QueueMetricsService queueMetricsService;

    /**
     * Main queue stats endpoint
     * GET /actuator/queue-stats
     */
    @ReadOperation
    public Map<String, Object> queueStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Get comprehensive queue metrics
            QueueMetricsService.QueueMetrics metrics = queueMetricsService.getQueueMetrics();

            // Summary information
            Map<String, Object> summary = new HashMap<>();
            summary.put("current_queue_size", metrics.currentQueueSize);
            summary.put("dead_letter_queue_size", metrics.deadLetterQueueSize);
            summary.put("total_jobs_processed", metrics.totalJobsProcessed);
            summary.put("total_jobs_enqueued", metrics.totalJobsEnqueued);
            summary.put("average_processing_time_ms", metrics.averageProcessingTimeMs);
            summary.put("last_updated", metrics.lastUpdated);

            stats.put("summary", summary);

            // Worker information
            Map<String, Object> workers = new HashMap<>();
            workers.put("total_workers", metrics.totalWorkers);
            workers.put("active_workers", metrics.activeWorkers);
            workers.put("worker_stats", metrics.workerStats);

            stats.put("workers", workers);

            // Performance indicators
            stats.put("performance", metrics.performanceIndicators);

            // Current period metrics
            stats.put("current_period", metrics.currentPeriodMetrics);

            // Historical trends (last 10 data points for brevity)
            if (metrics.historicalTrends != null && metrics.historicalTrends.size() > 10) {
                stats.put("recent_trends",
                        metrics.historicalTrends.subList(metrics.historicalTrends.size() - 10,
                                metrics.historicalTrends.size()));
            } else {
                stats.put("recent_trends", metrics.historicalTrends);
            }

            // Status
            stats.put("status", "healthy");
            stats.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            stats.put("status", "error");
            stats.put("error", e.getMessage());
            stats.put("timestamp", LocalDateTime.now());
        }

        return stats;
    }

    /**
     * Processing rate specific endpoint
     * GET /actuator/queue-stats/rates
     */
    @ReadOperation
    public Map<String, Object> processingRates(@Selector String selector) {
        if (!"rates".equals(selector)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Unknown selector: " + selector);
            error.put("available_selectors", new String[]{"rates"});
            return error;
        }

        Map<String, Object> rates = new HashMap<>();

        try {
            QueueMetricsService.ProcessingRateMetrics rateMetrics =
                    queueMetricsService.getProcessingRateMetrics();

            rates.put("jobs_per_minute", rateMetrics.jobsPerMinute);
            rates.put("success_rate", String.format("%.2f%%", rateMetrics.successRate * 100));
            rates.put("failure_rate", String.format("%.2f%%", rateMetrics.failureRate * 100));
            rates.put("current_throughput", rateMetrics.currentThroughput);
            rates.put("peak_throughput", rateMetrics.peakThroughput);
            rates.put("status", "healthy");
            rates.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            rates.put("status", "error");
            rates.put("error", e.getMessage());
            rates.put("timestamp", LocalDateTime.now());
        }

        return rates;
    }
}