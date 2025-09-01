package com.example.narayan.paymentsystem.queue;

import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;

import java.util.concurrent.LinkedBlockingQueue;

public class JobQueue {

    private final LinkedBlockingQueue<PaymentJob> queue = new LinkedBlockingQueue<>();

    public void enqueue(PaymentJob job){
        queue.offer(job);
    }
    public PaymentJob dequeue() throws InterruptedException{
        return queue.take();
    }
}
