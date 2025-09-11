package com.example.narayan.paymentsystem.worker;

import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.example.narayan.paymentsystem.queue.processor.JobProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WorkerManager {

    @Value("${payment.worker.count:workerCount}")
    private int workerCount;

    @Value("${payment.worker.polling-interval-ms:1000}")
    private long pollingIntervalMs;

    @Value("${payment.worker.error-backoff-ms:2000}")
    private long errorBackoffMs;

    @Value("${payment.worker.shutdown-timeout-seconds:30}")
    private long shutdownTimeoutSeconds;

    private final JobQueue jobQueue;
    private final JobProcessor<PaymentJob> jobProcessor;

    private ExecutorService executorService;
    private final List<JobWorker> workers = new ArrayList<>();
    private final List<Future<?>> workerFutures = new ArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Autowired
    public WorkerManager(JobQueue jobQueue, JobProcessor<PaymentJob> jobProcessor) {
        this.jobQueue = jobQueue;
        this.jobProcessor = jobProcessor;
    }

    @PostConstruct
    public void startWorkers(){
        if(started.compareAndSet(false, true)){
            System.out.println("🚀 Starting WorkerManager with " + workerCount + " workers");

            //Create thread pool for workers
            executorService = Executors.newFixedThreadPool(workerCount, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("payment-worker-"+ t.getId());
                return t;
            });

            //Create and start workers
            for(int i=0 ; i<workerCount ; i++){
                JobWorker worker = new JobWorker(jobQueue, jobProcessor, pollingIntervalMs, errorBackoffMs);
                workers.add(worker);

                Future<?> future = executorService.submit(worker);
                workerFutures.add(future);
            }
            System.out.println("✅ WorkerManager started with " + workers.size() + " active workers");

            // Start stats reporter thread
            startStatsReporter();
        }
    }

    @PreDestroy
    public void shutdownWorkers() {
        if (started.get()) {
            System.out.println("🔄 WorkerManager shutting down...");

            // Signal all workers to stop
            for (JobWorker worker : workers) {
                worker.shutdown();
            }

            // Wait for workers to finish current jobs
            if (executorService != null) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                        System.out.println("⚠️ Force shutdown - some workers didn't finish gracefully");
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            started.set(false);
            System.out.println("🛑 WorkerManager stopped");
        }
    }

    private void startStatsReporter(){
        Thread statsThread = new Thread(() ->{
            while (started.get()){
                try{
                    Thread.sleep(30000); //Report every 30 seconds
                    printWorkerStats();
                }
                catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        statsThread.setDaemon(true);
        statsThread.setName("worker-stats-reporter");
        statsThread.start();
    }

    public void printWorkerStats() {
        System.out.println("\n📊 === WORKER STATS ===");

        long totalProcessed = 0;
        long totalFailed = 0;
        long activeWorkers = 0;

        for(JobWorker worker : workers){
            JobWorker.WorkerStats stats = worker.getDetailedStats();
            System.out.println(" " + stats);

            totalProcessed += stats.processedJobs;
            totalFailed += stats.failedJobs;
            if(stats.isRunning) activeWorkers++;
        }
        System.out.println(String.format("  TOTALS: active=%d/%d, processed=%d, failed=%d, queue_size=%d",
                activeWorkers, workerCount, totalProcessed, totalFailed, jobQueue.size()));
        System.out.println("========================\n");
    }

    public String getOverallStats(){
        long totalProcessed = 0;
        long totalFailed = 0;
        int activeWorkers = 0;

        for (JobWorker worker : workers) {
            JobWorker.WorkerStats stats = worker.getDetailedStats();
            totalProcessed += stats.processedJobs;
            totalFailed += stats.failedJobs;
            if (stats.isRunning) activeWorkers++;
        }

        return String.format("Workers: %d/%d active, Processed: %d, Failed: %d, Queue: %d",
                activeWorkers, workerCount, totalProcessed, totalFailed, jobQueue.size());
    }

    public List<JobWorker.WorkerStats> getAllWorkerStats() {
        List<JobWorker.WorkerStats> allStats = new ArrayList<>();
        for (JobWorker worker : workers) {
            allStats.add(worker.getDetailedStats());
        }
        return allStats;
    }

    public boolean isStarted() {
        return started.get();
    }

    public int getWorkerCount() {
        return workers.size();
    }

    public int getActiveWorkerCount() {
        return (int) workers.stream().filter(JobWorker::isRunning).count();
    }

    //For testing/debugging
    public void addMoreWorkers(int count) {
        if (!started.get()) {
            throw new IllegalStateException("WorkerManager not started");
        }

        for (int i = 0; i < count; i++) {
            JobWorker worker = new JobWorker(jobQueue, jobProcessor, pollingIntervalMs, errorBackoffMs);
            workers.add(worker);

            Future<?> future = executorService.submit(worker);
            workerFutures.add(future);
        }

        System.out.println("➕ Added " + count + " more workers. Total: " + workers.size());
    }
}
