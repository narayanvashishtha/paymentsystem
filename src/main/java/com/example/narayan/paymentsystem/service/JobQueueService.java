package com.example.narayan.paymentsystem.service;

import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.jobs.JobStatus;
import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.example.narayan.paymentsystem.queue.jobs.JobResult;
import com.example.narayan.paymentsystem.queue.processor.PaymentJobProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JobQueueService {

    @Autowired
    JobQueue jobQueue;
    @Autowired
    PaymentJobProcessor paymentJobProcessor;


    private int processedCount = 0;
    private int failedCount = 0;

    //Add job into queue
    public void enqueuePayment(PaymentJob job) {
        jobQueue.enqueue(job);
    }

    //Take next job and process
    public JobResult processNextJob() {
        try {
            PaymentJob job = jobQueue.dequeue();

            if (job == null) {
                Thread.sleep(1000);
                return new JobResult(JobStatus.SKIPPED, "No jobs available, worker is idle");
            }

            JobResult result = paymentJobProcessor.process(job);
            processedCount++;
            return result;
        } catch (Exception e) {
            failedCount++;
            return new JobResult(
                    com.example.narayan.paymentsystem.queue.jobs.JobStatus.FAILED,
                    "Error processing job: " + e.getMessage()
            );
        }
    }

    public String getQueueStats() {
        return String.format("Processed=%d, Failed=%d, Pending=%d",
                processedCount, failedCount, jobQueueSize());
    }

    private int jobQueueSize() {
        return 0;
    }
}
