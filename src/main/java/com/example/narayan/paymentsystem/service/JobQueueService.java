package com.example.narayan.paymentsystem.service;

import com.example.narayan.paymentsystem.queue.RedisPriorityJobQueue;
import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.example.narayan.paymentsystem.queue.processor.PaymentJobProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JobQueueService {

    @Autowired
    RedisPriorityJobQueue jobQueue;
    @Autowired
    PaymentJobProcessor paymentJobProcessor;


    private int processedCount = 0;
    private int failedCount = 0;
    private boolean workerStarted = false;

    //Add job into queue
    public void enqueuePayment(PaymentJob job) {
        jobQueue.enqueue(job);
        if (!workerStarted) {
            paymentJobProcessor.startProcessor();
            workerStarted = true;
            System.out.println("Worker started after first job enqueued!");
        }
    }

    //Take next job and process
    public void recordJobStats(boolean status) {
        if (status) {
            processedCount++;
        } else {
            failedCount++;
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
