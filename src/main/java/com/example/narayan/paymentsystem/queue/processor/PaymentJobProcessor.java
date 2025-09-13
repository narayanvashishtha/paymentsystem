package com.example.narayan.paymentsystem.queue.processor;

import com.example.narayan.paymentsystem.model.Payment;
import com.example.narayan.paymentsystem.model.enums.PaymentStatus;
import com.example.narayan.paymentsystem.queue.DeadLetterQueue;
import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.failure.ExponentialBackoff;
import com.example.narayan.paymentsystem.queue.jobs.JobResult;
import com.example.narayan.paymentsystem.queue.jobs.JobStatus;
import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.example.narayan.paymentsystem.repository.PaymentRepository;
import com.example.narayan.paymentsystem.service.PaymentGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PaymentJobProcessor implements JobProcessor<PaymentJob> {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentGatewayService paymentGatewayService;

    @Autowired
    private JobQueue jobQueue;

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private DeadLetterQueue deadLetterQueue;

    @Autowired
    private ObjectMapper objectMapper;

    // Retry configuration - matches the requirement: 1s, 2s, 4s, 8s
    private static final long INITIAL_BACKOFF_MS = 1000;  // 1 second
    private static final long MAX_BACKOFF_MS = 8000;      // 8 seconds max
    private static final double BACKOFF_FACTOR = 2.0;     // Double each time

    private final ExponentialBackoff exponentialBackoff = new ExponentialBackoff(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS, BACKOFF_FACTOR);

    public void startProcessor() {
        Thread worker = new Thread(() -> {
            System.out.println("🚀 PaymentJobProcessor started");

            while (!Thread.currentThread().isInterrupted()) {
                try (var jedis = jedisPool.getResource()) {
                    // Check Redis connection
                    jedis.ping();

                    // Poll for jobs - this integrates with your existing JobQueue
                    PaymentJob job = jobQueue.dequeue();

                    if (job != null) {
                        // Check if job is scheduled for future processing (retry delay)
                        if (!job.isReadyToProcess()) {
                            System.out.println("⏰ Job " + job.getJobId() + " not ready yet, requeuing for " +
                                    job.getScheduledFor());
                            // Put it back in the queue for later
                            jobQueue.enqueue(job);
                            Thread.sleep(1000); // Wait a bit before checking again
                            continue;
                        }

                        // Process the job
                        JobResult result = process(job);
                        System.out.println("📊 Job " + job.getJobId() + " processed with status: " +
                                result.getStatus());
                    } else {
                        // No jobs available, wait a bit
                        Thread.sleep(1000);
                    }

                } catch (JedisConnectionException e) {
                    System.err.println("⚠️ Redis connection failed, retrying in 2s");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Unexpected error in processor: " + e.getMessage());
                    e.printStackTrace();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            System.out.println("🛑 PaymentJobProcessor stopped");
        });
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public JobResult process(PaymentJob job) {
        System.out.println("🔄 Processing payment job: " + job.getJobId() +
                " (attempt " + (job.getRetryCount() + 1) + "/" + job.getMaxTries() + ")");

        try {
            // Find the payment
            Payment payment = paymentRepository.findById(job.getPaymentId())
                    .orElseThrow(() -> new RuntimeException("Payment not found for job: " + job.getPaymentId()));

            // Process the payment
            paymentGatewayService.processPayment(payment.getId());

            // Success case
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setCompletedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            System.out.println("✅ Payment processed successfully: " + payment.getId());
            return new JobResult(JobStatus.COMPLETED, "Payment processed successfully");

        } catch (Exception e) {
            System.err.println("❌ Payment processing failed: " + e.getMessage());

            // Store the error in the job for debugging
            job.setLastError(e.getMessage());

            // Handle the failure with retry logic
            return handleJobFailure(job, e);
        }
    }

    /**
     * Handle job failure with retry logic
     */
    private JobResult handleJobFailure(PaymentJob job, Exception error) {
        job.incrementRetryCount();

        if (job.hasRetriesRemaining()) {
            // Calculate backoff delay for next attempt
            long backoffMs = exponentialBackoff.calculateBackoffMillis(job.getRetryCount());
            LocalDateTime nextAttemptTime = LocalDateTime.now().plusSeconds(backoffMs / 1000);
            job.setScheduledFor(nextAttemptTime);

            // Requeue the job for retry
            try {
                jobQueue.enqueue(job); // Use your existing JobQueue

                System.out.println("🔄 Job " + job.getJobId() + " scheduled for retry " +
                        job.getRetryCount() + " in " + (backoffMs/1000) + " seconds");

                return new JobResult(JobStatus.RETRYING,
                        "Job failed, retry " + job.getRetryCount() + " scheduled: " + error.getMessage());

            } catch (Exception e) {
                System.err.println("Failed to requeue job for retry: " + e.getMessage());
                // If we can't requeue, send to dead letter queue
                moveToDeadLetterQueue(job, "Failed to requeue for retry: " + e.getMessage());
                updatePaymentToFailed(job.getPaymentId().toString(), e.getMessage());
                return new JobResult(JobStatus.DEAD_LETTERED, "Job failed and couldn't be requeued");
            }
        }
        else {
            // No more retries remaining, move to dead letter queue
            moveToDeadLetterQueue(job, "Max retries exceeded (" + job.getRetryCount() + "). Last error: " + error.getMessage());

            // Update payment status to failed
            updatePaymentToFailed(job.getPaymentId().toString(), error.getMessage());

            System.out.println("🪦 Job " + job.getJobId() + " moved to dead letter queue after " +
                    job.getRetryCount() + " attempts");

            return new JobResult(JobStatus.DEAD_LETTERED,
                    "Job failed after " + job.getRetryCount() + " attempts: " + error.getMessage());
        }
    }

    /**
     * Move job to dead letter queue
     */
    private void moveToDeadLetterQueue(PaymentJob job, String reason) {
        deadLetterQueue.addToDeadLetterQueue(job, reason);
    }

    /**
     * Update payment status to failed
     */
    private void updatePaymentToFailed(String paymentId, String errorMessage) {
        try {
            Payment payment = paymentRepository.findById(UUID.fromString(paymentId)).orElse(null);
            if (payment != null) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(errorMessage);
                payment.setCompletedAt(LocalDateTime.now());
                paymentRepository.save(payment);
                System.out.println("💾 Payment " + paymentId + " marked as FAILED in database");
            } else {
                System.err.println("⚠️ Payment not found for ID: " + paymentId);
            }
        } catch (Exception e) {
            System.err.println("Failed to update payment status: " + e.getMessage());
        }
    }

    /**
     * Check if an exception is retryable or should go straight to dead letter queue
     */
    private boolean isRetryableException(Exception e) {
        // Network-related exceptions are usually retryable
        if (e instanceof java.net.SocketTimeoutException ||
                e instanceof java.net.ConnectException ||
                e instanceof java.io.IOException) {
            return true;
        }

        // Check error message for retryable conditions
        String message = e.getMessage().toLowerCase();
        if (message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("network") ||
                message.contains("service unavailable") ||
                message.contains("rate limit")) {
            return true;
        }

        // Non-retryable: validation errors, not found, etc.
        if (message.contains("not found") ||
                message.contains("invalid") ||
                message.contains("unauthorized") ||
                message.contains("forbidden")) {
            return false;
        }

        // Default to retryable for unknown exceptions
        return true;
    }

    /**
     * Get processor statistics
     */
    public int getProcessorStats() {
        try (var jedis = jedisPool.getResource()) {
            return (int) jedis.llen("job_queue:payments");
        } catch (Exception e) {
            System.err.println("Failed to get processor stats: " + e.getMessage());
            return 0;
        }
    }
}