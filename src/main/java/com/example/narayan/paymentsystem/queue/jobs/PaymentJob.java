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
    private int maxTries = 5;

    @Builder.Default
    @JsonProperty("createdAt")
    private Instant createdAt = Instant.now();

    @Builder.Default
    @JsonProperty("priority")
    private Priority priority = Priority.NORMAL;

    public enum Priority{
        LOW,
        NORMAL,
        CRITICAL,
        HIGH
    }

    // Convenience factory method
    public static PaymentJob of(UUID paymentId, int amount) {
        return PaymentJob.builder().paymentId(paymentId).amount(amount).build();
    }
}
