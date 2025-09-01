package com.example.narayan.paymentsystem.queue.jobs;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JobResult {
    JobStatus status;
    String message;
}
