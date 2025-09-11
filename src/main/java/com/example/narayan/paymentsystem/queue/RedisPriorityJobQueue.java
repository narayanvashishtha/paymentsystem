package com.example.narayan.paymentsystem.queue;

import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;


@Component
public class RedisPriorityJobQueue implements JobQueue{

    private static final String QUEUE_KEY = "payment_jobs";
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public RedisPriorityJobQueue(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public void enqueue(PaymentJob job) {
        int retries = 3;
        while (retries > 0) {
            try (Jedis jedis = jedisPool.getResource()) {
                String json = objectMapper.writeValueAsString(job);
                double priority = (job.getAmount() > 50000) ? 1 : 0;

                jedis.zadd(QUEUE_KEY, priority, json);
                return; // Success
            }
            catch (JedisConnectionException e) {
                retries--;
                if (retries == 0) {
                    System.err.println("❌ Failed to enqueue job after 3 attempts: " + e.getMessage());
                    throw new RuntimeException("Failed to enqueue job - Redis unavailable", e);
                }
                System.err.println("⚠️ Redis connection failed, retrying... (" + retries + " attempts left)");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting to retry", ie);
                }
            }
            catch (Exception e) {
                System.err.println("❌ Failed to enqueue job: " + e.getMessage());
                throw new RuntimeException("Failed to enqueue job", e);
            }
        }
    }

    @Override
    public PaymentJob dequeue() throws InterruptedException {
        try (Jedis jedis = jedisPool.getResource()) {
            var result = jedis.zpopmin(QUEUE_KEY, 1);
            if(result.isEmpty()){
                return null;
            }
            String json = result.getFirst().getElement();

            PaymentJob job = objectMapper.readValue(json, PaymentJob.class);
            return job;
        }
        catch (JedisConnectionException e) {
            // Don't throw exception - let workers handle the retry logic
            System.err.println("⚠️ Redis connection lost during dequeue: " + e.getMessage());
            return null;
        }
        catch (Exception e) {
            System.err.println("❌ Failed to dequeue job: " + e.getMessage());
            return null;
        }
    }

    @Override
    public int size() {
        try (Jedis jedis = jedisPool.getResource()) {
            return (int) (long) jedis.zcard(QUEUE_KEY);
        }
        catch (JedisConnectionException e) {
            System.err.println("⚠️ Redis connection lost during size check: " + e.getMessage());
            return 0;
        }
        catch (Exception e) {
            System.err.println("❌ Failed to get queue size: " + e.getMessage());
            return 0;
        }
    }

    public boolean isConnected() {
        try (Jedis jedis = jedisPool.getResource()) {
            String response = jedis.ping();
            return "PONG".equals(response);
        } catch (Exception e) {
            return false;
        }
    }
}