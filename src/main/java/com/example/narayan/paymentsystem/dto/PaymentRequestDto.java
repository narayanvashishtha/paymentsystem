package com.example.narayan.paymentsystem.dto;

import com.example.narayan.paymentsystem.model.enums.PaymentMethodType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
public class PaymentRequestDto {
    private UUID user_id;
    private UUID paymentMethod_id;

    @Size(min = 3, max = 3)
    private String currency;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    private BigDecimal amount;

    @Size(max = 255)
    private String idempotency_key;

    @JsonProperty("paymentMethod")
    private PaymentMethodType paymentMethodType;

    //Card-specific
    private String cardHolderName;
    private String cardNumber;
    private int expiryMonth;
    private int expiryYear;
    private String cvv;

    //UPI-specific
    private String upiId;
    private String pin;
}
