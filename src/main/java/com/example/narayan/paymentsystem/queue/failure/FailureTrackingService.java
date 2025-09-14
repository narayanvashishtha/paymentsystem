package com.example.narayan.paymentsystem.queue.failure;

import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FailureTrackingService {

    private static final String FAILURE_STATS_KEY = "job_queue:failure_stats";
    private static final String FAILURE_HISTORY_KEY = "job_queue:failure_history";
    private static final String FAILURE_BY_CATEGORY_KEY = "job_queue:failure_by_category";

    // Keep last 1000 failure records for analysis
    private static final int MAX_FAILURE_HISTORY = 1000;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Record a job failure with detailed analysis
     */
    public void recordFailure(PaymentJob job, Exception exception) {
        try (var jedis = jedisPool.getResource()) {
            FailureAnalysis analysis = FailureAnalysis.analyze(exception, job.getRetryCount() + 1);

            // Store detailed failure analysis
            String analysisJson = objectMapper.writeValueAsString(analysis);
            String failureKey = "failure:" + job.getJobId() + ":" + System.currentTimeMillis();

            // Add to failure history (with expiry)
            jedis.lpush(FAILURE_HISTORY_KEY, failureKey + ":" + analysisJson);
            jedis.ltrim(FAILURE_HISTORY_KEY, 0, MAX_FAILURE_HISTORY - 1);

            // Update category statistics
            jedis.hincrBy(FAILURE_BY_CATEGORY_KEY, analysis.getCategory().name(), 1);

            // Update overall stats
            jedis.hincrBy(FAILURE_STATS_KEY, "total_failures", 1);
            jedis.hincrBy(FAILURE_STATS_KEY, "retryable_failures", analysis.isRetryable() ? 1 : 0);
            jedis.hincrBy(FAILURE_STATS_KEY, "non_retryable_failures", analysis.isRetryable() ? 0 : 1);

            // Set expiry for cleanup (30 days)
            jedis.expire(FAILURE_HISTORY_KEY, 30 * 24 * 60 * 60);
            jedis.expire(FAILURE_BY_CATEGORY_KEY, 30 * 24 * 60 * 60);
            jedis.expire(FAILURE_STATS_KEY, 30 * 24 * 60 * 60);

            System.out.println("📊 Failure recorded: " + analysis.getCategory() + " for job " + job.getJobId());

        } catch (Exception e) {
            System.err.println("Failed to record failure analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get failure statistics by category
     */
    public Map<String, Object> getFailureStatistics() {
        try (var jedis = jedisPool.getResource()) {
            Map<String, Object> stats = new HashMap<>();

            // Overall stats
            Map<String, String> overallStats = jedis.hgetAll(FAILURE_STATS_KEY);
            stats.put("overall", overallStats);

            // By category
            Map<String, String> categoryStats = jedis.hgetAll(FAILURE_BY_CATEGORY_KEY);
            stats.put("by_category", categoryStats);

            // Recent failure trend (last 24 hours)
            List<String> recentFailures = jedis.lrange(FAILURE_HISTORY_KEY, 0, 100);
            long last24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

            Map<FailureCategory, Integer> recentTrends = new HashMap<>();
            for (String failure : recentFailures) {
                try {
                    String[] parts = failure.split(":", 3);
                    if (parts.length >= 3) {
                        long timestamp = Long.parseLong(parts[1]);
                        if (timestamp > last24Hours) {
                            FailureAnalysis analysis = objectMapper.readValue(parts[2], FailureAnalysis.class);
                            recentTrends.merge(analysis.getCategory(), 1, Integer::sum);
                        }
                    }
                } catch (Exception e) {
                    // Skip malformed entries
                }
            }

            stats.put("last_24h_trends", recentTrends);
            stats.put("generated_at", LocalDateTime.now());

            return stats;

        } catch (Exception e) {
            System.err.println("Failed to get failure statistics: " + e.getMessage());
            return Map.of("error", "Failed to retrieve statistics");
        }
    }

    /**
     * Get recent failure details for debugging
     */
    public List<FailureAnalysis> getRecentFailures(int limit) {
        try (var jedis = jedisPool.getResource()) {
            List<String> failureEntries = jedis.lrange(FAILURE_HISTORY_KEY, 0, limit - 1);

            return failureEntries.stream()
                    .map(entry -> {
                        try {
                            String[] parts = entry.split(":", 3);
                            if (parts.length >= 3) {
                                return objectMapper.readValue(parts[2], FailureAnalysis.class);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to parse failure entry: " + e.getMessage());
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Failed to get recent failures: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Clear failure statistics (for testing or cleanup)
     */
    public void clearFailureStats() {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(FAILURE_STATS_KEY);
            jedis.del(FAILURE_HISTORY_KEY);
            jedis.del(FAILURE_BY_CATEGORY_KEY);
            System.out.println("🧹 Failure statistics cleared");
        } catch (Exception e) {
            System.err.println("Failed to clear failure stats: " + e.getMessage());
        }
    }

    /**
     * Generate a failure analysis report
     */
    public String generateFailureReport() {
        Map<String, Object> stats = getFailureStatistics();
        StringBuilder report = new StringBuilder();

        report.append("=== FAILURE ANALYSIS REPORT ===\n");
        report.append("Generated at: ").append(LocalDateTime.now()).append("\n\n");

        // Overall statistics
        @SuppressWarnings("unchecked")
        Map<String, String> overall = (Map<String, String>) stats.get("overall");
        if (overall != null && !overall.isEmpty()) {
            report.append("OVERALL STATISTICS:\n");
            report.append("  Total Failures: ").append(overall.getOrDefault("total_failures", "0")).append("\n");
            report.append("  Retryable: ").append(overall.getOrDefault("retryable_failures", "0")).append("\n");
            report.append("  Non-Retryable: ").append(overall.getOrDefault("non_retryable_failures", "0")).append("\n\n");
        }

        // By category
        @SuppressWarnings("unchecked")
        Map<String, String> byCategory = (Map<String, String>) stats.get("by_category");
        if (byCategory != null && !byCategory.isEmpty()) {
            report.append("FAILURES BY CATEGORY:\n");
            byCategory.entrySet().stream()
                    .sorted(Map.Entry.<String, String>comparingByValue().reversed())
                    .forEach(entry -> {
                        FailureCategory category = FailureCategory.valueOf(entry.getKey());
                        report.append("  ").append(category.name()).append(": ")
                                .append(entry.getValue()).append(" (")
                                .append(category.getDescription()).append(")\n");
                    });
            report.append("\n");
        }

        // Recent trends
        @SuppressWarnings("unchecked")
        Map<FailureCategory, Integer> trends = (Map<FailureCategory, Integer>) stats.get("last_24h_trends");
        if (trends != null && !trends.isEmpty()) {
            report.append("LAST 24 HOURS TRENDS:\n");
            trends.entrySet().stream()
                    .sorted(Map.Entry.<FailureCategory, Integer>comparingByValue().reversed())
                    .forEach(entry -> report.append("  ").append(entry.getKey().name())
                            .append(": ").append(entry.getValue()).append("\n"));
        }

        return report.toString();
    }
}