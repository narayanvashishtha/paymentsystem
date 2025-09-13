package com.example.narayan.paymentsystem.queue;

import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeadLetterQueue {

    private static final String DEAD_LETTER_QUEUE_KEY = "job_queue:dead_letter";
    private static final String DEAD_LETTER_METADATA_KEY = "job_queue:dead_letter:metadata";

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Add a job to the dead letter queue
     */
    public void addToDeadLetterQueue(PaymentJob job, String reason) {
        try (var jedis = jedisPool.getResource()) {
            // Serialize the job
            String jobJson = objectMapper.writeValueAsString(job);

            // Add to dead letter queue
            jedis.lpush(DEAD_LETTER_QUEUE_KEY, jobJson);

            // Store metadata about why it failed
            Map<String, String> metadata = new HashMap<>();
            metadata.put("jobId", job.getJobId());
            metadata.put("failureReason", reason);
            metadata.put("failedAt", LocalDateTime.now().toString());
            metadata.put("paymentId", job.getPaymentId().toString());
            metadata.put("retryAttempts", String.valueOf(job.getRetryCount()));

            String metadataJson = objectMapper.writeValueAsString(metadata);
            jedis.hset(DEAD_LETTER_METADATA_KEY, job.getJobId(), metadataJson);

            System.out.println("🪦 Job " + job.getJobId() + " moved to dead letter queue: " + reason);

        } catch (Exception e) {
            System.err.println("Failed to add job to dead letter queue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get all jobs in the dead letter queue
     */
    public List<String> getDeadLetterJobs() {
        try (var jedis = jedisPool.getResource()) {
            return jedis.lrange(DEAD_LETTER_QUEUE_KEY, 0, -1);
        } catch (Exception e) {
            System.err.println("Failed to retrieve dead letter jobs: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get count of jobs in dead letter queue
     */
    public long getDeadLetterCount() {
        try (var jedis = jedisPool.getResource()) {
            return jedis.llen(DEAD_LETTER_QUEUE_KEY);
        } catch (Exception e) {
            System.err.println("Failed to get dead letter count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Clear all jobs from dead letter queue (use with caution!)
     */
    public void clearDeadLetterQueue() {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(DEAD_LETTER_QUEUE_KEY);
            jedis.del(DEAD_LETTER_METADATA_KEY);
            System.out.println("🧹 Dead letter queue cleared");
        } catch (Exception e) {
            System.err.println("Failed to clear dead letter queue: " + e.getMessage());
        }
    }

    /**
     * Get metadata for a specific failed job
     */
    public String getJobMetadata(String jobId) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.hget(DEAD_LETTER_METADATA_KEY, jobId);
        } catch (Exception e) {
            System.err.println("Failed to retrieve job metadata: " + e.getMessage());
            return null;
        }
    }
}