package com.example.narayan.paymentsystem.queue.failure;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @JsonCreator)
public class FailureAnalysis {

    @JsonProperty("category")
    private FailureCategory category;

    @JsonProperty("originalException")
    private String originalException;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("stackTrace")
    private String stackTrace;

    @JsonProperty("isRetryable")
    private boolean isRetryable;

    @JsonProperty("failedAt")
    private LocalDateTime failedAt;

    @JsonProperty("attemptNumber")
    private int attemptNumber;

    public static FailureAnalysis analyze(Exception e, int attemptNumber) {
        FailureCategory category = FailureCategory.categorize(e);

        return new FailureAnalysis(
                category,
                e.getClass().getSimpleName(),
                e.getMessage() != null ? e.getMessage() : "No error message",
                getStackTraceAsString(e),
                category.isRetryable(),
                LocalDateTime.now(),
                attemptNumber
        );
    }

    private static String getStackTraceAsString(Exception e) {
        if (e == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");

        StackTraceElement[] elements = e.getStackTrace();
        // Only include first 5 stack trace elements to avoid bloat
        int limit = Math.min(5, elements.length);

        for (int i = 0; i < limit; i++) {
            sb.append("\tat ").append(elements[i].toString()).append("\n");
        }

        if (elements.length > limit) {
            sb.append("\t... ").append(elements.length - limit).append(" more");
        }

        return sb.toString();
    }

    public String getAnalysisReport() {
        return String.format(
                "Failure Analysis:\n" +
                        "  Category: %s (%s)\n" +
                        "  Exception: %s\n" +
                        "  Message: %s\n" +
                        "  Retryable: %s\n" +
                        "  Attempt: %d\n" +
                        "  Occurred: %s",
                category, category.getDescription(),
                originalException,
                errorMessage,
                isRetryable ? "Yes" : "No",
                attemptNumber,
                failedAt
        );
    }
}