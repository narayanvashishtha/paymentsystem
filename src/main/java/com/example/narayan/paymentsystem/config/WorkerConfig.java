package com.example.narayan.paymentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "payment.worker")
@Data
public class WorkerConfig {

    //Number of worker threads to run concurrently
    private int count = 3;

    //How often workers should poll for jobs (milliseconds)
    private long pollingIntervalMs = 1000;

    //How long to wait before retrying after an error (milliseconds)
    private long errorBackoffMs = 2000;

    //Maximum time to wait for workers to shutdown gracefully (seconds)
    private long shutdownTimeoutSeconds = 30;

    //Whether to enable detailed stats reporting
    private boolean enableStats = true;

    //How often to print stats (seconds)
    private long statsIntervalSeconds = 30;

    /*
     Maximum number of jobs a single worker should process before restarting
     (helps prevent memory leaks in long-running workers)
    */
    private long maxJobsPerWorker = 10000;
}