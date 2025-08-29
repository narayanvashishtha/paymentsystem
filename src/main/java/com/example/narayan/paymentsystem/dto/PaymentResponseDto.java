package com.example.narayan.paymentsystem.dto;

import com.example.narayan.paymentsystem.model.enums.PaymentMethodType;
import com.example.narayan.paymentsystem.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponseDto {
    private UUID paymentId;
    private PaymentStatus status;
    private String gatewayTransactionId;
    private String failureReason;
    private LocalDateTime completedAt;
    private String currency;
    private BigDecimal amount;
    private String message;
}
