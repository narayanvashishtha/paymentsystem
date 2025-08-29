package com.example.narayan.paymentsystem.exception;

public class PaymentNotFound extends RuntimeException {
    public PaymentNotFound(String message) {
        super(message);
    }

    public PaymentNotFound(String message, Throwable cause) {
        super(message, cause);
    }
}
