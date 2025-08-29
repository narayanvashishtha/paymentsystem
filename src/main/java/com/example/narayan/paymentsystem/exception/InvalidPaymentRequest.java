package com.example.narayan.paymentsystem.exception;

public class InvalidPaymentRequest extends RuntimeException {
    public InvalidPaymentRequest(String message) {
        super(message);
    }

    public InvalidPaymentRequest(String message, Throwable cause) {
        super(message, cause);
    }
}
