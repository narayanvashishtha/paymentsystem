package com.example.narayan.paymentsystem.worker;

import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.jobs.JobResult;
import com.example.narayan.paymentsystem.queue.jobs.JobStatus;
import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.example.narayan.paymentsystem.queue.processor.JobProcessor;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class JobWorker implements Runnable {

    private static final AtomicLong workerIdGenerator = new AtomicLong(1);

    private final long workerId;
    private final JobQueue jobQueue;
    private final JobProcessor<PaymentJob> jobProcessor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    // Configurable polling interval (milliseconds)
    private final long pollingIntervalMs;
    private final long errorBackoffMs;

    // Statistics with processing times
    private final AtomicLong processedJobs = new AtomicLong(0);
    private final AtomicLong failedJobs = new AtomicLong(0);
    private final AtomicLong emptyPolls = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private volatile long minProcessingTimeMs = Long.MAX_VALUE;
    private volatile long maxProcessingTimeMs = 0;

    public JobWorker(JobQueue jobQueue, JobProcessor<PaymentJob> jobProcessor) {
        this(jobQueue, jobProcessor, 1000, 2000); // Default: 1s polling, 2s error backoff
    }

    public JobWorker(JobQueue jobQueue, JobProcessor<PaymentJob> jobProcessor,
                     long pollingIntervalMs, long errorBackoffMs) {
        this.workerId = workerIdGenerator.getAndIncrement();
        this.jobQueue = jobQueue;
        this.jobProcessor = jobProcessor;
        this.pollingIntervalMs = pollingIntervalMs;
        this.errorBackoffMs = errorBackoffMs;
    }

    @Override
    public void run() {
        running.set(true);
        System.out.println("🚀 JobWorker-" + workerId + " started (polling every " + pollingIntervalMs + "ms)");

        while (!shutdown.get()) {
            try {
                // Poll for next job
                PaymentJob job = jobQueue.dequeue();

                if (job != null) {
                    processJob(job);
                } else {
                    // No jobs available - increment empty polls and wait
                    emptyPolls.incrementAndGet();
                    Thread.sleep(pollingIntervalMs);
                }

            } catch (JedisConnectionException e) {
                handleRedisConnectionError(e);
            } catch (InterruptedException e) {
                System.out.println("⚠️ JobWorker-" + workerId + " interrupted, shutting down gracefully");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("❌ JobWorker-" + workerId + " unexpected error: " + e.getMessage());
                e.printStackTrace();

                // Backoff on unexpected errors
                try {
                    Thread.sleep(errorBackoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        running.set(false);
        System.out.println("🛑 JobWorker-" + workerId + " stopped gracefully. Final stats: " + getStats());
    }

    private void processJob(PaymentJob job) {
        long startTime = System.currentTimeMillis();
        System.out.println("🔄 JobWorker-" + workerId + " processing job: " + job.getPaymentId());

        try {
            JobResult result = jobProcessor.process(job);
            long processingTime = System.currentTimeMillis() - startTime;

            // Update processing time statistics
            updateProcessingTimeStats(processingTime);

            if (result.getStatus() == JobStatus.COMPLETED) {
                processedJobs.incrementAndGet();
                System.out.println("✅ JobWorker-" + workerId + " completed job: " + job.getPaymentId() +
                        " in " + processingTime + "ms");
            } else {
                failedJobs.incrementAndGet();
                System.out.println("❌ JobWorker-" + workerId + " failed job: " + job.getPaymentId() +
                        " in " + processingTime + "ms - " + result.getMessage());
            }

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            failedJobs.incrementAndGet();
            updateProcessingTimeStats(processingTime);

            System.err.println("💥 JobWorker-" + workerId + " exception processing job " +
                    job.getPaymentId() + " in " + processingTime + "ms: " + e.getMessage());
        }
    }

    private void updateProcessingTimeStats(long processingTime) {
        totalProcessingTimeMs.addAndGet(processingTime);

        // Update min/max (thread-safe approach)
        long currentMin = minProcessingTimeMs;
        while (processingTime < currentMin) {
            if (minProcessingTimeMs == currentMin) {
                minProcessingTimeMs = processingTime;
                break;
            }
            currentMin = minProcessingTimeMs;
        }

        long currentMax = maxProcessingTimeMs;
        while (processingTime > currentMax) {
            if (maxProcessingTimeMs == currentMax) {
                maxProcessingTimeMs = processingTime;
                break;
            }
            currentMax = maxProcessingTimeMs;
        }
    }

    private void handleRedisConnectionError(JedisConnectionException e) {
        System.err.println("⚠️ JobWorker-" + workerId + " Redis connection lost, waiting " +
                (errorBackoffMs / 1000) + "s before retry...");
        try {
            Thread.sleep(errorBackoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        System.out.println("🔄 JobWorker-" + workerId + " shutdown requested");
        shutdown.set(true);
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getWorkerId() {
        return workerId;
    }

    public String getStats() {
        long totalJobs = processedJobs.get() + failedJobs.get();
        long avgProcessingTime = totalJobs > 0 ? totalProcessingTimeMs.get() / totalJobs : 0;

        return String.format("Worker-%d: processed=%d, failed=%d, empty_polls=%d, avg_time=%dms",
                workerId, processedJobs.get(), failedJobs.get(), emptyPolls.get(), avgProcessingTime);
    }

    public WorkerStats getDetailedStats() {
        long totalJobs = processedJobs.get() + failedJobs.get();
        long avgProcessingTime = totalJobs > 0 ? totalProcessingTimeMs.get() / totalJobs : 0;
        long safeMinTime = minProcessingTimeMs == Long.MAX_VALUE ? 0 : minProcessingTimeMs;

        return new WorkerStats(
                workerId,
                running.get(),
                processedJobs.get(),
                failedJobs.get(),
                emptyPolls.get(),
                avgProcessingTime,
                safeMinTime,
                maxProcessingTimeMs
        );
    }

    public static class WorkerStats {
        public final long workerId;
        public final boolean isRunning;
        public final long processedJobs;
        public final long failedJobs;
        public final long emptyPolls;
        public final long avgProcessingTimeMs;
        public final long minProcessingTimeMs;
        public final long maxProcessingTimeMs;

        public WorkerStats(long workerId, boolean isRunning, long processedJobs,
                           long failedJobs, long emptyPolls, long avgProcessingTimeMs,
                           long minProcessingTimeMs, long maxProcessingTimeMs) {
            this.workerId = workerId;
            this.isRunning = isRunning;
            this.processedJobs = processedJobs;
            this.failedJobs = failedJobs;
            this.emptyPolls = emptyPolls;
            this.avgProcessingTimeMs = avgProcessingTimeMs;
            this.minProcessingTimeMs = minProcessingTimeMs;
            this.maxProcessingTimeMs = maxProcessingTimeMs;
        }

        @Override
        public String toString() {
            return String.format("WorkerStats{id=%d, running=%s, processed=%d, failed=%d, empty=%d, avg=%dms, min=%dms, max=%dms}",
                    workerId, isRunning, processedJobs, failedJobs, emptyPolls,
                    avgProcessingTimeMs, minProcessingTimeMs, maxProcessingTimeMs);
        }
    }
}