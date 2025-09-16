package com.example.narayan.paymentsystem.service.monitoring;

import com.example.narayan.paymentsystem.config.AlertingConfig;
import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.DeadLetterQueue;
import com.example.narayan.paymentsystem.worker.WorkerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AlertingService {

    private static final Logger logger = LoggerFactory.getLogger(AlertingService.class);
    private static final Logger alertLogger = LoggerFactory.getLogger("ALERTS");

    // Alert state tracking to prevent spam
    private final Map<String, Boolean> activeAlerts = new HashMap<>();
    private final Map<String, LocalDateTime> lastAlertTime = new HashMap<>();
    private final AtomicLong totalAlertsGenerated = new AtomicLong(0);

    @Autowired
    private JobQueue jobQueue;

    @Autowired
    private DeadLetterQueue deadLetterQueue;

    @Autowired
    private WorkerManager workerManager;

    @Autowired
    private QueueMetricsService queueMetricsService;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private AlertingConfig alertingConfig;

    private final AtomicBoolean alertingEnabled = new AtomicBoolean(true);

    @PostConstruct
    public void initialize() {
        logger.info("🚨 AlertingService initialized with thresholds:");
        logger.info("  Queue WARNING: {} jobs, CRITICAL: {} jobs", alertingConfig.getQueueWarningThreshold(), alertingConfig.getQueueCriticalThreshold());
        logger.info("  Dead Letter WARNING: {} jobs, CRITICAL: {} jobs", alertingConfig.getDeadLetterWarningThreshold(), alertingConfig.getDeadLetterCriticalThreshold());
        logger.info("  Worker Utilization WARNING: {}%", (int)(alertingConfig.getWorkerUtilizationWarning() * 100));
        logger.info("  Failure Rate WARNING: {}%, CRITICAL: {}%", (int)(alertingConfig.getFailureRateWarning() * 100), (int)(alertingConfig.getFailureRateCritical() * 100));
        logger.info("  Processing Time WARNING: {}s, CRITICAL: {}s", alertingConfig.getProcessingTimeWarningMs()/1000, alertingConfig.getProcessingTimeCriticalMs()/1000);
    }

    /**
     * Main alerting check - runs every 30 seconds
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void checkSystemHealth() {
        if (!alertingEnabled.get()) {
            return;
        }

        try {
            logger.debug("🔍 Running system health checks...");

            // Get current metrics
            QueueMetricsService.QueueMetrics metrics = queueMetricsService.getQueueMetrics();
            QueueMetricsService.ProcessingRateMetrics rateMetrics = queueMetricsService.getProcessingRateMetrics();

            // Check each alert condition
            checkQueueSizeAlerts(metrics);
            checkDeadLetterQueueAlerts(metrics);
            checkWorkerAlerts(metrics);
            checkPerformanceAlerts(metrics, rateMetrics);
            checkFailureRateAlerts(rateMetrics);

            // Log summary every 5 minutes
            logPeriodicSummary();

        } catch (Exception e) {
            logger.error("❌ Error during system health check: {}", e.getMessage(), e);
            generateAlert("SYSTEM_ERROR", AlertLevel.CRITICAL,
                    "System health check failed: " + e.getMessage());
        }
    }

    /**
     * Check queue size thresholds
     */
    private void checkQueueSizeAlerts(QueueMetricsService.QueueMetrics metrics) {
        long queueSize = metrics.currentQueueSize;
        String alertKey = "QUEUE_SIZE";

        if (queueSize >= alertingConfig.getQueueCriticalThreshold()) {
            generateAlert(alertKey + "_CRITICAL", AlertLevel.CRITICAL,
                    String.format("Queue size is CRITICAL: %d jobs (threshold: %d)",
                            queueSize, alertingConfig.getQueueCriticalThreshold()));
        } else if (queueSize >= alertingConfig.getQueueWarningThreshold()) {
            generateAlert(alertKey + "_WARNING", AlertLevel.WARNING,
                    String.format("Queue size is high: %d jobs (threshold: %d)",
                            queueSize, alertingConfig.getQueueWarningThreshold()));
        } else {
            clearAlert(alertKey + "_WARNING");
            clearAlert(alertKey + "_CRITICAL");
        }
    }

    /**
     * Check dead letter queue thresholds
     */
    private void checkDeadLetterQueueAlerts(QueueMetricsService.QueueMetrics metrics) {
        long dlqSize = metrics.deadLetterQueueSize;
        String alertKey = "DEAD_LETTER_QUEUE";

        if (dlqSize >= alertingConfig.getDeadLetterCriticalThreshold()) {
            generateAlert(alertKey + "_CRITICAL", AlertLevel.CRITICAL,
                    String.format("Dead Letter Queue is CRITICAL: %d jobs (threshold: %d)",
                            dlqSize, alertingConfig.getDeadLetterCriticalThreshold()));
        } else if (dlqSize >= alertingConfig.getDeadLetterWarningThreshold()) {
            generateAlert(alertKey + "_WARNING", AlertLevel.WARNING,
                    String.format("Dead Letter Queue is growing: %d jobs (threshold: %d)",
                            dlqSize, alertingConfig.getDeadLetterWarningThreshold()));
        } else {
            clearAlert(alertKey + "_WARNING");
            clearAlert(alertKey + "_CRITICAL");
        }
    }

    /**
     * Check worker-related alerts
     */
    private void checkWorkerAlerts(QueueMetricsService.QueueMetrics metrics) {
        // Check if no workers are active but queue has jobs
        if (metrics.activeWorkers == 0 && metrics.currentQueueSize > 0) {
            generateAlert("NO_ACTIVE_WORKERS", AlertLevel.CRITICAL,
                    String.format("NO ACTIVE WORKERS but queue has %d jobs pending", metrics.currentQueueSize));
        } else {
            clearAlert("NO_ACTIVE_WORKERS");
        }

        // Check worker utilization
        if (metrics.totalWorkers > 0) {
            double utilization = (double) metrics.activeWorkers / metrics.totalWorkers;

            if (utilization >= alertingConfig.getWorkerUtilizationWarning()) {
                generateAlert("HIGH_WORKER_UTILIZATION", AlertLevel.WARNING,
                        String.format("High worker utilization: %.1f%% (%d/%d workers active)",
                                utilization * 100, metrics.activeWorkers, metrics.totalWorkers));
            } else {
                clearAlert("HIGH_WORKER_UTILIZATION");
            }
        }

        // Check if WorkerManager is not started
        if (!workerManager.isStarted()) {
            generateAlert("WORKER_MANAGER_DOWN", AlertLevel.CRITICAL,
                    "WorkerManager is not started - no job processing possible");
        } else {
            clearAlert("WORKER_MANAGER_DOWN");
        }
    }

    /**
     * Check performance-related alerts
     */
    private void checkPerformanceAlerts(QueueMetricsService.QueueMetrics metrics,
                                        QueueMetricsService.ProcessingRateMetrics rateMetrics) {

        // Check average processing time
        long avgProcessingTime = metrics.averageProcessingTimeMs;

        if (avgProcessingTime >= alertingConfig.getProcessingTimeCriticalMs()) {
            generateAlert("SLOW_PROCESSING_CRITICAL", AlertLevel.CRITICAL,
                    String.format("Processing time is CRITICAL: %dms (threshold: %dms)",
                            avgProcessingTime, alertingConfig.getProcessingTimeCriticalMs()));
        } else if (avgProcessingTime >= alertingConfig.getProcessingTimeWarningMs()) {
            generateAlert("SLOW_PROCESSING_WARNING", AlertLevel.WARNING,
                    String.format("Processing time is slow: %dms (threshold: %dms)",
                            avgProcessingTime, alertingConfig.getProcessingTimeWarningMs()));
        } else {
            clearAlert("SLOW_PROCESSING_WARNING");
            clearAlert("SLOW_PROCESSING_CRITICAL");
        }

        // Check if throughput is zero but there are jobs
        if (rateMetrics.currentThroughput == 0 && metrics.currentQueueSize > 0) {
            generateAlert("ZERO_THROUGHPUT", AlertLevel.CRITICAL,
                    String.format("Zero throughput detected with %d jobs in queue", metrics.currentQueueSize));
        } else {
            clearAlert("ZERO_THROUGHPUT");
        }
    }

    /**
     * Check failure rate alerts
     */
    private void checkFailureRateAlerts(QueueMetricsService.ProcessingRateMetrics rateMetrics) {
        double failureRate = rateMetrics.failureRate;

        if (failureRate >= alertingConfig.getFailureRateCritical()) {
            generateAlert("HIGH_FAILURE_RATE_CRITICAL", AlertLevel.CRITICAL,
                    String.format("Failure rate is CRITICAL: %.1f%% (threshold: %.1f%%)",
                            failureRate * 100, alertingConfig.getFailureRateCritical() * 100));
        } else if (failureRate >= alertingConfig.getFailureRateWarning()) {
            generateAlert("HIGH_FAILURE_RATE_WARNING", AlertLevel.WARNING,
                    String.format("Failure rate is high: %.1f%% (threshold: %.1f%%)",
                            failureRate * 100, alertingConfig.getFailureRateWarning() * 100));
        } else {
            clearAlert("HIGH_FAILURE_RATE_WARNING");
            clearAlert("HIGH_FAILURE_RATE_CRITICAL");
        }
    }

    /**
     * Generate an alert with cooldown logic
     */
    private void generateAlert(String alertKey, AlertLevel level, String message) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastAlert = lastAlertTime.get(alertKey);

        // Check cooldown period
        if (lastAlert != null &&
                now.isBefore(lastAlert.plusMinutes(alertingConfig.getAlertCooldownMinutes()))) {
            return; // Still in cooldown period
        }

        // Log the alert
        String logMessage = String.format("🚨 [%s] %s: %s", level, alertKey, message);

        switch (level) {
            case CRITICAL -> alertLogger.error(logMessage);
            case WARNING -> alertLogger.warn(logMessage);
            case INFO -> alertLogger.info(logMessage);
        }

        // Track alert state
        activeAlerts.put(alertKey, true);
        lastAlertTime.put(alertKey, now);
        totalAlertsGenerated.incrementAndGet();

        // Store alert in Redis for external monitoring tools
        storeAlertInRedis(alertKey, level, message, now);
    }

    /**
     * Clear an alert when condition is resolved
     */
    private void clearAlert(String alertKey) {
        if (activeAlerts.getOrDefault(alertKey, false)) {
            activeAlerts.put(alertKey, false);
            alertLogger.info("✅ [RESOLVED] {}: Alert condition cleared", alertKey);

            // Remove from Redis
            clearAlertFromRedis(alertKey);
        }
    }

    /**
     * Store alert in Redis for external monitoring
     */
    private void storeAlertInRedis(String alertKey, AlertLevel level, String message, LocalDateTime timestamp) {
        try (var jedis = jedisPool.getResource()) {
            String alertData = String.format("{\"level\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                    level, message, timestamp);

            jedis.hset("payment_system:alerts:active", alertKey, alertData);
            jedis.expire("payment_system:alerts:active", 3600); // Expire in 1 hour

            // Also add to alert history
            jedis.lpush("payment_system:alerts:history", alertKey + ":" + alertData);
            jedis.ltrim("payment_system:alerts:history", 0, 99); // Keep last 100 alerts

        } catch (Exception e) {
            logger.error("Failed to store alert in Redis: {}", e.getMessage());
        }
    }

    /**
     * Clear alert from Redis
     */
    private void clearAlertFromRedis(String alertKey) {
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel("payment_system:alerts:active", alertKey);
        } catch (Exception e) {
            logger.error("Failed to clear alert from Redis: {}", e.getMessage());
        }
    }

    /**
     * Log periodic summary (every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void logPeriodicSummary() {
        try {
            QueueMetricsService.QueueMetrics metrics = queueMetricsService.getQueueMetrics();

            long activeAlertsCount = activeAlerts.values().stream()
                    .mapToLong(active -> active ? 1 : 0)
                    .sum();

            logger.info("📊 System Status Summary - Queue: {}, DLQ: {}, Workers: {}/{}, Active Alerts: {}, Total Alerts: {}",
                    metrics.currentQueueSize,
                    metrics.deadLetterQueueSize,
                    metrics.activeWorkers,
                    metrics.totalWorkers,
                    activeAlertsCount,
                    totalAlertsGenerated.get());

        } catch (Exception e) {
            logger.error("Failed to log periodic summary: {}", e.getMessage());
        }
    }

    /**
     * Manual trigger for testing alerts
     */
    public void testAlert(AlertLevel level, String message) {
        generateAlert("TEST_ALERT", level, "TEST: " + message);
    }

    /**
     * Enable/disable alerting
     */
    public void setAlertingEnabled(boolean enabled) {
        alertingEnabled.set(enabled);
        logger.info("🚨 Alerting {}", enabled ? "ENABLED" : "DISABLED");
    }

    public boolean isAlertingEnabled() {
        return alertingEnabled.get();
    }

    /**
     * Get current alert status
     */
    public Map<String, Object> getAlertStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("alerting_enabled", alertingEnabled.get());
        status.put("active_alerts_count", activeAlerts.values().stream().mapToLong(active -> active ? 1 : 0).sum());
        status.put("total_alerts_generated", totalAlertsGenerated.get());
        status.put("active_alerts", new HashMap<>(activeAlerts));
        status.put("last_check", LocalDateTime.now());

        return status;
    }

    /**
     * Clear all active alerts (for testing/reset)
     */
    public void clearAllAlerts() {
        activeAlerts.clear();
        lastAlertTime.clear();

        try (var jedis = jedisPool.getResource()) {
            jedis.del("payment_system:alerts:active");
        } catch (Exception e) {
            logger.error("Failed to clear alerts from Redis: {}", e.getMessage());
        }

        alertLogger.info("🧹 All alerts cleared");
    }

    /**
     * Alert levels
     */
    public enum AlertLevel {
        INFO, WARNING, CRITICAL
    }
}