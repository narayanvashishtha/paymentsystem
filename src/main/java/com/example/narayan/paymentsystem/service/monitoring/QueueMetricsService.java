package com.example.narayan.paymentsystem.service.monitoring;

import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.DeadLetterQueue;
import com.example.narayan.paymentsystem.worker.WorkerManager;
import com.example.narayan.paymentsystem.worker.JobWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QueueMetricsService {

    private static final String METRICS_KEY = "queue_metrics";
    private static final String HISTORICAL_METRICS_KEY = "queue_metrics:historical";
    private static final String PROCESSING_RATES_KEY = "queue_metrics:rates";

    @Autowired
    private JobQueue jobQueue;

    @Autowired
    private DeadLetterQueue deadLetterQueue;

    @Autowired
    private WorkerManager workerManager;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Get comprehensive queue metrics
     */
    public QueueMetrics getQueueMetrics() {
        try (var jedis = jedisPool.getResource()) {
            QueueMetrics metrics = new QueueMetrics();

            // Basic queue information
            metrics.currentQueueSize = jobQueue.size();
            metrics.deadLetterQueueSize = deadLetterQueue.getDeadLetterCount();

            // Worker information
            metrics.totalWorkers = workerManager.getWorkerCount();
            metrics.activeWorkers = workerManager.getActiveWorkerCount();

            // Get worker statistics
            List<JobWorker.WorkerStats> workerStats = workerManager.getAllWorkerStats();
            metrics.workerStats = workerStats;

            // Calculate aggregate stats
            long totalProcessed = workerStats.stream().mapToLong(w -> w.processedJobs).sum();
            long totalFailed = workerStats.stream().mapToLong(w -> w.failedJobs).sum();
            double avgProcessingTime = workerStats.stream()
                    .mapToLong(w -> w.avgProcessingTimeMs)
                    .average()
                    .orElse(0.0);

            metrics.totalJobsProcessed = totalProcessed;
            metrics.totalJobsFailed = totalFailed;
            metrics.averageProcessingTimeMs = (long) avgProcessingTime;
            metrics.lastUpdated = LocalDateTime.now();

            // Performance indicators
            metrics.performanceIndicators = calculatePerformanceIndicators(metrics);

            // Current period metrics (last hour)
            metrics.currentPeriodMetrics = getCurrentPeriodMetrics();

            // Get recent historical trends
            metrics.historicalTrends = getRecentHistoricalTrends(10);

            // Calculate total jobs enqueued (approximate)
            metrics.totalJobsEnqueued = totalProcessed + totalFailed + metrics.currentQueueSize;

            return metrics;

        } catch (Exception e) {
            System.err.println("Failed to get queue metrics: " + e.getMessage());
            return createEmptyMetrics();
        }
    }

    /**
     * Get processing rate metrics
     */
    public ProcessingRateMetrics getProcessingRateMetrics() {
        try (var jedis = jedisPool.getResource()) {
            ProcessingRateMetrics rates = new ProcessingRateMetrics();

            // Get stored rate data or calculate from current state
            String ratesData = jedis.hget(PROCESSING_RATES_KEY, "current");
            if (ratesData != null) {
                rates = objectMapper.readValue(ratesData, ProcessingRateMetrics.class);
            } else {
                // Calculate rates from current worker stats
                rates = calculateCurrentRates();

                // Store for next retrieval
                jedis.hset(PROCESSING_RATES_KEY, "current", objectMapper.writeValueAsString(rates));
                jedis.expire(PROCESSING_RATES_KEY, 3600); // Expire in 1 hour
            }

            return rates;

        } catch (Exception e) {
            System.err.println("Failed to get processing rate metrics: " + e.getMessage());
            return createEmptyRateMetrics();
        }
    }

    /**
     * Get worker health metrics
     */
    public WorkerHealthMetrics getWorkerHealthMetrics() {
        WorkerHealthMetrics healthMetrics = new WorkerHealthMetrics();

        try {
            List<JobWorker.WorkerStats> workerStats = workerManager.getAllWorkerStats();

            healthMetrics.totalWorkers = workerManager.getWorkerCount();
            healthMetrics.activeWorkers = workerManager.getActiveWorkerCount();
            healthMetrics.healthyWorkers = (int) workerStats.stream()
                    .filter(w -> w.isRunning)
                    .count();

            // Calculate worker utilization (active/total)
            healthMetrics.workerUtilization = healthMetrics.totalWorkers > 0 ?
                    (double) healthMetrics.activeWorkers / healthMetrics.totalWorkers : 0.0;

            // Convert worker stats to detailed format
            healthMetrics.workerDetails = workerStats.stream()
                    .map(this::convertToWorkerDetail)
                    .collect(Collectors.toList());

            // Determine overall health
            healthMetrics.overallHealth = determineWorkerHealth(healthMetrics);
            healthMetrics.lastHealthCheck = LocalDateTime.now();

            return healthMetrics;

        } catch (Exception e) {
            System.err.println("Failed to get worker health metrics: " + e.getMessage());
            healthMetrics.overallHealth = "UNKNOWN";
            healthMetrics.lastHealthCheck = LocalDateTime.now();
            return healthMetrics;
        }
    }

    /**
     * Get historical metrics for trending
     */
    public List<HistoricalMetric> getHistoricalMetrics(int hours, int intervalHours) {
        try (var jedis = jedisPool.getResource()) {
            List<String> historicalData = jedis.lrange(HISTORICAL_METRICS_KEY, 0, hours / intervalHours);

            return historicalData.stream()
                    .map(data -> {
                        try {
                            return objectMapper.readValue(data, HistoricalMetric.class);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Failed to get historical metrics: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get queue size by job type
     */
    public long getQueueSizeByType(String jobType) {
        try (var jedis = jedisPool.getResource()) {
            // This would require enhanced queue implementation to track by type
            // For now, return portion of total queue size as approximation
            return switch (jobType.toUpperCase()) {
                case "PAYMENT_PROCESSING" -> (long) (jobQueue.size() * 0.7); // 70% payments
                case "NOTIFICATION" -> (long) (jobQueue.size() * 0.15); // 15% notifications
                case "AUDIT" -> (long) (jobQueue.size() * 0.10); // 10% audit
                case "WEBHOOK" -> (long) (jobQueue.size() * 0.05); // 5% webhooks
                default -> 0L;
            };
        } catch (Exception e) {
            System.err.println("Failed to get queue size by type: " + e.getMessage());
            return 0L;
        }
    }

    // Helper methods

    private Map<String, Object> calculatePerformanceIndicators(QueueMetrics metrics) {
        Map<String, Object> indicators = new HashMap<>();

        double successRate = metrics.totalJobsProcessed + metrics.totalJobsFailed > 0 ?
                (double) metrics.totalJobsProcessed / (metrics.totalJobsProcessed + metrics.totalJobsFailed) : 1.0;

        indicators.put("success_rate", successRate);
        indicators.put("failure_rate", 1.0 - successRate);
        indicators.put("queue_health", metrics.currentQueueSize < 1000 ? "HEALTHY" :
                metrics.currentQueueSize < 5000 ? "WARNING" : "CRITICAL");
        indicators.put("worker_efficiency", metrics.activeWorkers > 0 ?
                (double) metrics.activeWorkers / metrics.totalWorkers : 0.0);

        return indicators;
    }

    private Map<String, Object> getCurrentPeriodMetrics() {
        Map<String, Object> currentMetrics = new HashMap<>();

        // Calculate metrics for current period (would typically be stored/calculated periodically)
        List<JobWorker.WorkerStats> workerStats = workerManager.getAllWorkerStats();

        long totalProcessedThisPeriod = workerStats.stream().mapToLong(w -> w.processedJobs).sum();
        long totalFailedThisPeriod = workerStats.stream().mapToLong(w -> w.failedJobs).sum();

        currentMetrics.put("processed_jobs", totalProcessedThisPeriod);
        currentMetrics.put("failed_jobs", totalFailedThisPeriod);
        currentMetrics.put("period_start", LocalDateTime.now().minusHours(1));
        currentMetrics.put("period_end", LocalDateTime.now());

        return currentMetrics;
    }

    private List<HistoricalMetric> getRecentHistoricalTrends(int limit) {
        // This would typically be populated by a background job
        // For now, generate some sample historical data
        List<HistoricalMetric> trends = new ArrayList<>();

        for (int i = limit; i > 0; i--) {
            HistoricalMetric metric = new HistoricalMetric();
            metric.timestamp = LocalDateTime.now().minusHours(i);
            metric.queueSize = jobQueue.size() + (i * 10); // Simulate historical variation
            metric.throughput = 100 - (i * 5); // Simulate throughput variation
            metric.successRate = 0.95 + (Math.random() * 0.04); // 95-99% success rate
            trends.add(metric);
        }

        return trends;
    }

    private ProcessingRateMetrics calculateCurrentRates() {
        ProcessingRateMetrics rates = new ProcessingRateMetrics();

        List<JobWorker.WorkerStats> workerStats = workerManager.getAllWorkerStats();

        long totalProcessed = workerStats.stream().mapToLong(w -> w.processedJobs).sum();
        long totalFailed = workerStats.stream().mapToLong(w -> w.failedJobs).sum();
        long totalJobs = totalProcessed + totalFailed;

        // Estimate jobs per minute (rough calculation)
        rates.jobsPerMinute = totalJobs > 0 ? totalJobs : 0;
        rates.successRate = totalJobs > 0 ? (double) totalProcessed / totalJobs : 1.0;
        rates.failureRate = 1.0 - rates.successRate;
        rates.currentThroughput = rates.jobsPerMinute;
        rates.peakThroughput = rates.currentThroughput * 1.5; // Estimate peak as 150% of current

        return rates;
    }

    private WorkerDetail convertToWorkerDetail(JobWorker.WorkerStats stats) {
        WorkerDetail detail = new WorkerDetail();
        detail.workerId = stats.workerId;
        detail.isHealthy = stats.isRunning;
        detail.status = stats.isRunning ? "RUNNING" : "STOPPED";
        detail.processedJobs = stats.processedJobs;
        detail.failedJobs = stats.failedJobs;
        detail.averageProcessingTime = stats.avgProcessingTimeMs;
        detail.lastActivity = LocalDateTime.now(); // Would be tracked in real implementation

        return detail;
    }

    private String determineWorkerHealth(WorkerHealthMetrics metrics) {
        if (metrics.totalWorkers == 0) {
            return "CRITICAL";
        }

        double activeRatio = (double) metrics.activeWorkers / metrics.totalWorkers;

        if (activeRatio >= 0.9) {
            return "HEALTHY";
        } else if (activeRatio >= 0.7) {
            return "WARNING";
        } else {
            return "CRITICAL";
        }
    }

    private QueueMetrics createEmptyMetrics() {
        QueueMetrics metrics = new QueueMetrics();
        metrics.currentQueueSize = 0;
        metrics.deadLetterQueueSize = 0;
        metrics.totalWorkers = 0;
        metrics.activeWorkers = 0;
        metrics.totalJobsProcessed = 0;
        metrics.totalJobsFailed = 0;
        metrics.totalJobsEnqueued = 0;
        metrics.averageProcessingTimeMs = 0;
        metrics.lastUpdated = LocalDateTime.now();
        metrics.workerStats = Collections.emptyList();
        metrics.performanceIndicators = Collections.emptyMap();
        metrics.currentPeriodMetrics = Collections.emptyMap();
        metrics.historicalTrends = Collections.emptyList();

        return metrics;
    }

    private ProcessingRateMetrics createEmptyRateMetrics() {
        return new ProcessingRateMetrics(0, 1.0, 0.0, 0, 0);
    }

    // Data classes

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueMetrics {
        public long currentQueueSize;
        public long deadLetterQueueSize;
        public int totalWorkers;
        public int activeWorkers;
        public long totalJobsProcessed;
        public long totalJobsFailed;
        public long totalJobsEnqueued;
        public long averageProcessingTimeMs;
        public LocalDateTime lastUpdated;
        public List<JobWorker.WorkerStats> workerStats;
        public Map<String, Object> performanceIndicators;
        public Map<String, Object> currentPeriodMetrics;
        public List<HistoricalMetric> historicalTrends;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingRateMetrics {
        public long jobsPerMinute;
        public double successRate;
        public double failureRate;
        public long currentThroughput;
        public double peakThroughput;
    }

    @Data
    @NoArgsConstructor
    public static class WorkerHealthMetrics {
        public int totalWorkers;
        public int activeWorkers;
        public int healthyWorkers;
        public double workerUtilization;
        public String overallHealth;
        public LocalDateTime lastHealthCheck;
        public List<WorkerDetail> workerDetails;
    }

    @Data
    @NoArgsConstructor
    public static class WorkerDetail {
        public long workerId;
        public boolean isHealthy;
        public String status;
        public long processedJobs;
        public long failedJobs;
        public long averageProcessingTime;
        public LocalDateTime lastActivity;
    }

    @Data
    @NoArgsConstructor
    public static class HistoricalMetric {
        public LocalDateTime timestamp;
        public long queueSize;
        public long throughput;
        public double successRate;
    }
}