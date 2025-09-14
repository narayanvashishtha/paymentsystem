package com.example.narayan.paymentsystem.queue.processor;

import com.example.narayan.paymentsystem.model.Payment;
import com.example.narayan.paymentsystem.model.enums.PaymentStatus;
import com.example.narayan.paymentsystem.queue.DeadLetterQueue;
import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.failure.ExponentialBackoff;
import com.example.narayan.paymentsystem.queue.failure.FailureAnalysis;
import com.example.narayan.paymentsystem.queue.failure.FailureCategory;
import com.example.narayan.paymentsystem.queue.failure.FailureTrackingService;
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

    @Autowired
    private FailureTrackingService failureTrackingService;

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

            // Record failure with detailed analysis
            failureTrackingService.recordFailure(job, e);

            // Store the error in the job for debugging
            job.setLastError(e.getMessage());

            // Handle the failure with retry logic
            return handleJobFailure(job, e);
        }
    }

    /**
     * Handle job failure with enhanced retry logic and failure analysis
     */
    private JobResult handleJobFailure(PaymentJob job, Exception error) {
        // Analyze the failure to determine if it's retryable
        FailureAnalysis analysis = FailureAnalysis.analyze(error, job.getRetryCount() + 1);

        System.out.println("🔍 Failure Analysis: " + analysis.getAnalysisReport());

        job.incrementRetryCount();

        // Check if failure is retryable and we have retries remaining
        if (analysis.isRetryable() && job.hasRetriesRemaining()) {
            // Calculate backoff delay for next attempt
            long backoffMs = exponentialBackoff.calculateBackoffMillis(job.getRetryCount());
            LocalDateTime nextAttemptTime = LocalDateTime.now().plusSeconds(backoffMs / 1000);
            job.setScheduledFor(nextAttemptTime);

            // Requeue the job for retry
            try {
                jobQueue.enqueue(job);

                System.out.println("🔄 Job " + job.getJobId() + " scheduled for retry " +
                        job.getRetryCount() + " in " + (backoffMs/1000) + " seconds (Category: " +
                        analysis.getCategory() + ")");

                return new JobResult(JobStatus.RETRYING,
                        "Job failed (" + analysis.getCategory() + "), retry " + job.getRetryCount() +
                                " scheduled: " + error.getMessage());

            } catch (Exception e) {
                System.err.println("Failed to requeue job for retry: " + e.getMessage());
                moveToDeadLetterQueue(job, "Failed to requeue for retry: " + e.getMessage(), analysis);
                updatePaymentToFailed(job.getPaymentId().toString(), e.getMessage());
                return new JobResult(JobStatus.DEAD_LETTERED, "Job failed and couldn't be requeued");
            }
        }
        else {
            // Either not retryable or no more retries remaining
            String reason = !analysis.isRetryable() ?
                    "Non-retryable failure (" + analysis.getCategory() + "): " + error.getMessage() :
                    "Max retries exceeded (" + job.getRetryCount() + "). Last error: " + error.getMessage();

            moveToDeadLetterQueue(job, reason, analysis);
            updatePaymentToFailed(job.getPaymentId().toString(), error.getMessage());

            System.out.println("🪦 Job " + job.getJobId() + " moved to dead letter queue. Reason: " +
                    analysis.getCategory() + " - " +
                    (analysis.isRetryable() ? "Max retries exceeded" : "Non-retryable failure"));

            return new JobResult(JobStatus.DEAD_LETTERED, reason);
        }
    }

    /**
     * Move job to dead letter queue with failure analysis
     */
    private void moveToDeadLetterQueue(PaymentJob job, String reason, FailureAnalysis analysis) {
        try {
            // Enhanced reason with failure category
            String enhancedReason = String.format("%s | Category: %s | Retryable: %s | Analysis: %s",
                    reason, analysis.getCategory(), analysis.isRetryable(),
                    analysis.getErrorMessage());

            deadLetterQueue.addToDeadLetterQueue(job, enhancedReason);
        } catch (Exception e) {
            deadLetterQueue.addToDeadLetterQueue(job, reason); // Fallback to original method
        }
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

    /**
     * Get failure analysis report
     */
    public String getFailureAnalysisReport() {
        return failureTrackingService.generateFailureReport();
    }
}