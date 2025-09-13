package com.example.narayan.paymentsystem.queue.jobs;

public enum JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRYING,        // Added for retry logic
    DEAD_LETTERED,   // Added for dead letter queue
    RETRY_SCHEDULED,
    SKIPPED
}