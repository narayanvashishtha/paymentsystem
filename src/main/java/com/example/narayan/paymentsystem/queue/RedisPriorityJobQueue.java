package com.example.narayan.paymentsystem.queue;

import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;


@Component
public class RedisPriorityJobQueue implements JobQueue{

    private static final String QUEUE_KEY = "payment_jobs";
    private final Jedis jedis;
    private final ObjectMapper objectMapper;

    public RedisPriorityJobQueue(Jedis jedis) {
        this.jedis = jedis;
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper for proper serialization
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public void enqueue(PaymentJob job) {
        try {
            String json = objectMapper.writeValueAsString(job);
            double priority = (job.getAmount() > 50000) ? 1 : 0;

            jedis.zadd(QUEUE_KEY, priority, json);
        }
        catch (Exception e) {
            System.err.println("Failed to enqueue job: " + e.getMessage());
            throw new RuntimeException("Failed to enqueue job", e);
        }
    }

    @Override
    public PaymentJob dequeue() throws InterruptedException {
        try {
            var result = jedis.zpopmin(QUEUE_KEY,1);
            if(result.isEmpty()){
                return null;
            }
            String json = result.getFirst().getElement();

            PaymentJob job = objectMapper.readValue(json, PaymentJob.class);
            return job;
        }
        catch (Exception e) {
            System.err.println("Failed to dequeue job: " + e.getMessage());
            throw new RuntimeException("Failed to dequeue job", e);
        }
    }

    @Override
    public int size() {
        return (int) (long) jedis.zcard(QUEUE_KEY);
    }
}
