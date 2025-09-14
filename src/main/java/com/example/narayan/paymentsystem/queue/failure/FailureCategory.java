package com.example.narayan.paymentsystem.queue.failure;

public enum FailureCategory {
    GATEWAY_ERROR("Gateway/Payment processor error"),
    TIMEOUT("Request timeout or processing timeout"),
    VALIDATION_ERROR("Input validation or business rule violation"),
    NETWORK_ERROR("Network connectivity issues"),
    DATABASE_ERROR("Database connection or constraint violations"),
    UNKNOWN("Unclassified error");

    private final String description;

    FailureCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Categorize an exception into the appropriate failure category
     */
    public static FailureCategory categorize(Exception e) {
        if (e == null) {
            return UNKNOWN;
        }

        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String className = e.getClass().getSimpleName().toLowerCase();

        // Timeout errors
        if (className.contains("timeout") || message.contains("timeout") ||
                className.contains("socketTimeout") || message.contains("read timed out")) {
            return TIMEOUT;
        }

        // Network errors
        if (className.contains("connection") || message.contains("connection") ||
                className.contains("network") || message.contains("network") ||
                className.contains("socket") || message.contains("host unreachable")) {
            return NETWORK_ERROR;
        }

        // Database errors
        if (className.contains("sql") || className.contains("database") ||
                className.contains("constraint") || message.contains("constraint") ||
                className.contains("duplicate") || message.contains("unique")) {
            return DATABASE_ERROR;
        }

        // Validation errors
        if (className.contains("validation") || message.contains("invalid") ||
                className.contains("argument") || message.contains("validation") ||
                message.contains("required") || message.contains("must be")) {
            return VALIDATION_ERROR;
        }

        // Gateway errors
        if (message.contains("gateway") || message.contains("payment") ||
                message.contains("declined") || message.contains("insufficient") ||
                message.contains("card") || message.contains("upi") ||
                className.contains("payment")) {
            return GATEWAY_ERROR;
        }

        return UNKNOWN;
    }

    /**
     * Determine if this failure category should be retried
     */
    public boolean isRetryable() {
        return switch (this) {
            case GATEWAY_ERROR, TIMEOUT, NETWORK_ERROR -> true;
            case VALIDATION_ERROR, DATABASE_ERROR -> false;
            case UNKNOWN -> true; // Default to retryable for unknown errors
        };
    }
}