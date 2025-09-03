package com.example.narayan.paymentsystem.queue.jobs;

import com.fasterxml.jackson.annotation.JsonCreator;
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
@AllArgsConstructor(onConstructor_ = @JsonCreator)
public class PaymentJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    @NotNull
    private UUID paymentId;
    @NotNull
    private int amount;
    @Min(0)
    @Builder.Default
    private int retryCount = 0;
    @Min(0)
    @Builder.Default
    private int maxTries = 5;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
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
