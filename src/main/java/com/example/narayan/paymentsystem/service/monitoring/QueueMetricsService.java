package com.example.narayan.paymentsystem.service.monitoring;

import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.DeadLetterQueue;
import com.example.narayan.paymentsystem.queue.failure.FailureTrackingService;
import com.example.narayan.paymentsystem.worker.JobWorker;
import com.example.narayan.paymentsystem.worker.WorkerManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class QueueMetricsService {

    @Autowired
    private JobQueue jobQueue;

    @Autowired
    private DeadLetterQueue deadLetterQueue;

    @Autowired
    private WorkerManager workerManager;

    @Autowired
    private FailureTrackingService failureTrackingService;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private ObjectMapper objectMapper;

    // Metrics tracking
    private final AtomicLong totalJobsProcessed = new AtomicLong(0);
    private final AtomicLong totalJobsEnqueued = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);

    // Historical data (last 24 hours in 5-minute windows)
    private final int HISTORY_WINDOW_MINUTES = 5;
    private final int MAX_HISTORY_POINTS = 288; // 24 hours / 5 minutes
    private final LinkedList<MetricSnapshot> historicalMetrics = new LinkedList<>();

    // Current period tracking
    private MetricSnapshot currentSnapshot = new MetricSnapshot();
    private LocalDateTime lastSnapshotTime = LocalDateTime.now();

    /**
     * Record job enqueue event
     */
    public void recordJobEnqueued() {
        totalJobsEnqueued.incrementAndGet();
        currentSnapshot.jobsEnqueued++;
    }

    /**
     * Record job processing completion
     */
    public void recordJobProcessed(boolean success, long processingTimeMs) {
        totalJobsProcessed.incrementAndGet();
        totalProcessingTimeMs.addAndGet(processingTimeMs);

        if (success) {
            currentSnapshot.jobsSucceeded++;
        } else {
            currentSnapshot.jobsFailed++;
        }

        currentSnapshot.totalProcessingTime += processingTimeMs;
        currentSnapshot.jobsProcessed++;
    }

    /**
     * Get comprehensive queue metrics
     */
    public QueueMetrics getQueueMetrics() {
        QueueMetrics metrics = new QueueMetrics();

        // Current queue state
        metrics.currentQueueSize = jobQueue.size();
        metrics.deadLetterQueueSize = (int) deadLetterQueue.getDeadLetterCount();

        // Worker metrics
        metrics.totalWorkers = workerManager.getWorkerCount();
        metrics.activeWorkers = workerManager.getActiveWorkerCount();
        metrics.workerStats = workerManager.getAllWorkerStats();

        // Processing metrics
        metrics.totalJobsProcessed = totalJobsProcessed.get();
        metrics.totalJobsEnqueued = totalJobsEnqueued.get();

        long avgProcessingTime = totalJobsProcessed.get() > 0 ?
                totalProcessingTimeMs.get() / totalJobsProcessed.get() : 0;
        metrics.averageProcessingTimeMs = avgProcessingTime;

        // Current period metrics
        metrics.currentPeriodMetrics = getCurrentPeriodMetrics();

        // Historical trends
        metrics.historicalTrends = getHistoricalTrends();

        // Failure analysis
        metrics.failureStatistics = failureTrackingService.getFailureStatistics();

        // Performance indicators
        metrics.performanceIndicators = calculatePerformanceIndicators();

        metrics.lastUpdated = LocalDateTime.now();

        return metrics;
    }

    /**
     * Get processing rate metrics
     */
    public ProcessingRateMetrics getProcessingRateMetrics() {
        ProcessingRateMetrics rates = new ProcessingRateMetrics();

        // Calculate rates based on historical data
        if (!historicalMetrics.isEmpty()) {
            MetricSnapshot recent = historicalMetrics.getLast();
            rates.jobsPerMinute = (double) recent.jobsProcessed / HISTORY_WINDOW_MINUTES;
            rates.successRate = recent.jobsProcessed > 0 ?
                    (double) recent.jobsSucceeded / recent.jobsProcessed : 0.0;
            rates.failureRate = recent.jobsProcessed > 0 ?
                    (double) recent.jobsFailed / recent.jobsProcessed : 0.0;
        }

        // Current throughput
        rates.currentThroughput = calculateCurrentThroughput();
        rates.peakThroughput = calculatePeakThroughput();

        return rates;
    }

    /**
     * Get worker health metrics
     */
    public WorkerHealthMetrics getWorkerHealthMetrics() {
        WorkerHealthMetrics health = new WorkerHealthMetrics();

        health.totalWorkers = workerManager.getWorkerCount();
        health.activeWorkers = workerManager.getActiveWorkerCount();
        health.healthyWorkers = calculateHealthyWorkers();
        health.workerUtilization = calculateWorkerUtilization();

        // Individual worker health
        health.workerDetails = new ArrayList<>();
        for (JobWorker.WorkerStats stats : workerManager.getAllWorkerStats()) {
            WorkerDetail detail = new WorkerDetail();
            detail.workerId = stats.workerId;
            detail.isHealthy = stats.isRunning && stats.processedJobs > 0;
            detail.processedJobs = stats.processedJobs;
            detail.failedJobs = stats.failedJobs;
            detail.averageProcessingTime = stats.avgProcessingTimeMs;
            detail.status = stats.isRunning ? "ACTIVE" : "INACTIVE";
            health.workerDetails.add(detail);
        }

        health.overallHealth = calculateOverallWorkerHealth();
        health.lastHealthCheck = LocalDateTime.now();

        return health;
    }

    /**
     * Take periodic snapshots for historical tracking
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void takePeriodicSnapshot() {
        // Complete current snapshot
        currentSnapshot.timestamp = LocalDateTime.now();
        currentSnapshot.queueSizeAtSnapshot = jobQueue.size();
        currentSnapshot.deadLetterSizeAtSnapshot = (int) deadLetterQueue.getDeadLetterCount();

        // Add to history
        synchronized (historicalMetrics) {
            historicalMetrics.addLast(currentSnapshot);

            // Keep only last 24 hours
            while (historicalMetrics.size() > MAX_HISTORY_POINTS) {
                historicalMetrics.removeFirst();
            }
        }

        // Reset for next period
        currentSnapshot = new MetricSnapshot();
        lastSnapshotTime = LocalDateTime.now();

        System.out.println("📊 Metrics snapshot taken - Queue: " + jobQueue.size() +
                ", Dead Letter: " + deadLetterQueue.getDeadLetterCount());
    }

    private CurrentPeriodMetrics getCurrentPeriodMetrics() {
        CurrentPeriodMetrics current = new CurrentPeriodMetrics();
        current.periodStartTime = lastSnapshotTime;
        current.jobsProcessedThisPeriod = currentSnapshot.jobsProcessed;
        current.jobsSucceededThisPeriod = currentSnapshot.jobsSucceeded;
        current.jobsFailedThisPeriod = currentSnapshot.jobsFailed;
        current.averageProcessingTimeThisPeriod = currentSnapshot.jobsProcessed > 0 ?
                currentSnapshot.totalProcessingTime / currentSnapshot.jobsProcessed : 0;
        return current;
    }

    private List<TrendDataPoint> getHistoricalTrends() {
        List<TrendDataPoint> trends = new ArrayList<>();

        synchronized (historicalMetrics) {
            for (MetricSnapshot snapshot : historicalMetrics) {
                TrendDataPoint point = new TrendDataPoint();
                point.timestamp = snapshot.timestamp;
                point.queueSize = snapshot.queueSizeAtSnapshot;
                point.jobsProcessed = snapshot.jobsProcessed;
                point.successRate = snapshot.jobsProcessed > 0 ?
                        (double) snapshot.jobsSucceeded / snapshot.jobsProcessed : 0.0;
                point.averageProcessingTime = snapshot.jobsProcessed > 0 ?
                        snapshot.totalProcessingTime / snapshot.jobsProcessed : 0;
                trends.add(point);
            }
        }

        return trends;
    }

    private PerformanceIndicators calculatePerformanceIndicators() {
        PerformanceIndicators indicators = new PerformanceIndicators();

        // Queue health
        int queueSize = jobQueue.size();
        indicators.queueHealthStatus = queueSize < 100 ? "HEALTHY" :
                queueSize < 500 ? "WARNING" : "CRITICAL";

        // Worker efficiency
        int activeWorkers = workerManager.getActiveWorkerCount();
        int totalWorkers = workerManager.getWorkerCount();
        indicators.workerEfficiency = totalWorkers > 0 ?
                (double) activeWorkers / totalWorkers : 0.0;

        // Processing efficiency
        indicators.averageProcessingTime = totalJobsProcessed.get() > 0 ?
                totalProcessingTimeMs.get() / totalJobsProcessed.get() : 0;

        // Failure rate
        Map<String, Object> failureStats = failureTrackingService.getFailureStatistics();
        @SuppressWarnings("unchecked")
        Map<String, String> overall = (Map<String, String>) failureStats.get("overall");
        if (overall != null) {
            int totalFailures = Integer.parseInt(overall.getOrDefault("total_failures", "0"));
            long totalProcessed = totalJobsProcessed.get();
            indicators.overallFailureRate = (totalProcessed + totalFailures) > 0 ?
                    (double) totalFailures / (totalProcessed + totalFailures) : 0.0;
        }

        return indicators;
    }

    private double calculateCurrentThroughput() {
        return currentSnapshot.jobsProcessed > 0 ?
                (double) currentSnapshot.jobsProcessed /
                        ((System.currentTimeMillis() - lastSnapshotTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) / 60000.0) : 0.0;
    }

    private double calculatePeakThroughput() {
        return historicalMetrics.stream()
                .mapToDouble(s -> (double) s.jobsProcessed / HISTORY_WINDOW_MINUTES)
                .max()
                .orElse(0.0);
    }

    private int calculateHealthyWorkers() {
        return (int) workerManager.getAllWorkerStats().stream()
                .filter(stats -> stats.isRunning)
                .count();
    }

    private double calculateWorkerUtilization() {
        List<JobWorker.WorkerStats> allStats = workerManager.getAllWorkerStats();
        if (allStats.isEmpty()) return 0.0;

        long totalJobs = allStats.stream()
                .mapToLong(stats -> stats.processedJobs + stats.failedJobs)
                .sum();

        return totalJobs > 0 ? 1.0 : 0.0; // Simplified - could be more sophisticated
    }

    private String calculateOverallWorkerHealth() {
        int total = workerManager.getWorkerCount();
        int active = workerManager.getActiveWorkerCount();

        if (total == 0) return "NO_WORKERS";
        if (active == total) return "HEALTHY";
        if (active >= total * 0.8) return "WARNING";
        return "CRITICAL";
    }

    // Data classes for metrics
    public static class QueueMetrics {
        public int currentQueueSize;
        public int deadLetterQueueSize;
        public int totalWorkers;
        public int activeWorkers;
        public List<JobWorker.WorkerStats> workerStats;
        public long totalJobsProcessed;
        public long totalJobsEnqueued;
        public long averageProcessingTimeMs;
        public CurrentPeriodMetrics currentPeriodMetrics;
        public List<TrendDataPoint> historicalTrends;
        public Map<String, Object> failureStatistics;
        public PerformanceIndicators performanceIndicators;
        public LocalDateTime lastUpdated;
    }

    public static class ProcessingRateMetrics {
        public double jobsPerMinute;
        public double successRate;
        public double failureRate;
        public double currentThroughput;
        public double peakThroughput;
    }

    public static class WorkerHealthMetrics {
        public int totalWorkers;
        public int activeWorkers;
        public int healthyWorkers;
        public double workerUtilization;
        public List<WorkerDetail> workerDetails;
        public String overallHealth;
        public LocalDateTime lastHealthCheck;
    }

    public static class WorkerDetail {
        public long workerId;
        public boolean isHealthy;
        public long processedJobs;
        public long failedJobs;
        public long averageProcessingTime;
        public String status;
    }

    public static class CurrentPeriodMetrics {
        public LocalDateTime periodStartTime;
        public int jobsProcessedThisPeriod;
        public int jobsSucceededThisPeriod;
        public int jobsFailedThisPeriod;
        public long averageProcessingTimeThisPeriod;
    }

    public static class TrendDataPoint {
        public LocalDateTime timestamp;
        public int queueSize;
        public int jobsProcessed;
        public double successRate;
        public long averageProcessingTime;
    }

    public static class PerformanceIndicators {
        public String queueHealthStatus;
        public double workerEfficiency;
        public long averageProcessingTime;
        public double overallFailureRate;
    }

    private static class MetricSnapshot {
        LocalDateTime timestamp;
        int jobsEnqueued = 0;
        int jobsProcessed = 0;
        int jobsSucceeded = 0;
        int jobsFailed = 0;
        long totalProcessingTime = 0;
        int queueSizeAtSnapshot = 0;
        int deadLetterSizeAtSnapshot = 0;
    }
}