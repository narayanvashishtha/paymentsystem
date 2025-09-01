package com.example.narayan.paymentsystem.queue.jobs;

public enum JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRY_SCHEDULED
}
