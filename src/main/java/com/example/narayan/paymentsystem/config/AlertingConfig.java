package com.example.narayan.paymentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "payment.alerting")
@Data
public class AlertingConfig {

    // Queue thresholds
    private int queueWarningThreshold = 100;
    private int queueCriticalThreshold = 1000;

    // Dead letter queue thresholds
    private int deadLetterWarningThreshold = 10;
    private int deadLetterCriticalThreshold = 100;

    // Worker thresholds
    private double workerUtilizationWarning = 0.8; // 80%

    // Performance thresholds
    private long processingTimeWarningMs = 10000; // 10 seconds
    private long processingTimeCriticalMs = 30000; // 30 seconds

    // Failure rate thresholds
    private double failureRateWarning = 0.1; // 10%
    private double failureRateCritical = 0.2; // 20%

    // Alert behavior
    private int alertCooldownMinutes = 5;
    private boolean enabled = true;
    private int checkIntervalSeconds = 30;
    private int summaryIntervalMinutes = 5;

    // Redis storage
    private int alertHistoryLimit = 100;
    private int activeAlertTtlSeconds = 3600; // 1 hour
}