package com.example.narayan.paymentsystem.queue.failure;

import java.util.Random;

public class ExponentialBackoff {

    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final double backoffFactor;
    private final Random random;

    public ExponentialBackoff(long initialBackoffMillis, long maxBackoffMillis, double backoffFactor) {
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
        this.backoffFactor = backoffFactor;
        this.random = new Random();
    }

    public long calculateBackoffMillis(int attempt) {
        if (attempt <= 0) {
            throw new IllegalArgumentException("Attempt number must be positive.");
        }

        // Calculate base exponential backoff
        long calculatedBackoff = (long) (initialBackoffMillis * Math.pow(backoffFactor, attempt - 1));

        // Apply max backoff limit
        long effectiveBackoff = Math.min(calculatedBackoff, maxBackoffMillis);

        // Add jitter to prevent thundering herd
        double jitterFactor = 0.5 + (random.nextDouble() * 1.0); // Range [0.5, 1.5]
        return (long) (effectiveBackoff * jitterFactor);
    }
}