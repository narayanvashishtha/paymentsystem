package com.example.narayan.paymentsystem.queue.processor;

import com.example.narayan.paymentsystem.queue.jobs.JobResult;
import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;

public interface JobProcessor<P> {

    JobResult process(PaymentJob job);
}
