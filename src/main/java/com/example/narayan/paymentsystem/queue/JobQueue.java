package com.example.narayan.paymentsystem.queue;

import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;


public interface JobQueue {

    public void enqueue(PaymentJob job);

    public PaymentJob dequeue() throws InterruptedException;

    int size();
}
