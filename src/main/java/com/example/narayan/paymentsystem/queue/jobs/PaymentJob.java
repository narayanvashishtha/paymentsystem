package com.example.narayan.paymentsystem.queue.jobs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @JsonCreator)
public class PaymentJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @JsonProperty("paymentId")
    private UUID paymentId;

    @NotNull
    @JsonProperty("amount")
    private int amount;

    @Min(0)
    @Builder.Default
    @JsonProperty("retryCount")
    private int retryCount = 0;

    @Min(0)
    @Builder.Default
    @JsonProperty("maxTries")
    private int maxTries = 4; // Changed to 4 as per requirement

    @Builder.Default
    @JsonProperty("createdAt")
    private Instant createdAt = Instant.now();

    @Builder.Default
    @JsonProperty("priority")
    private Priority priority = Priority.NORMAL;

    // New fields for retry logic
    @JsonProperty("scheduledFor")
    private LocalDateTime scheduledFor = LocalDateTime.now();

    @JsonProperty("lastError")
    private String lastError;

    @JsonProperty("jobId")
    private String jobId = UUID.randomUUID().toString();

    public enum Priority{
        LOW,
        NORMAL,
        CRITICAL,
        HIGH
    }

    // Helper methods for retry logic
    public boolean hasRetriesRemaining() {
        return retryCount < maxTries;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean isReadyToProcess() {
        return LocalDateTime.now().isAfter(scheduledFor) ||
                LocalDateTime.now().isEqual(scheduledFor);
    }

    // Convenience factory method
    public static PaymentJob of(UUID paymentId, int amount) {
        return PaymentJob.builder()
                .paymentId(paymentId)
                .amount(amount)
                .jobId(UUID.randomUUID().toString())
                .build();
    }
}