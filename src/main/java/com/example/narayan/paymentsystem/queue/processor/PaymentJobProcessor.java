package com.example.narayan.paymentsystem.queue.processor;

import com.example.narayan.paymentsystem.model.Payment;
import com.example.narayan.paymentsystem.model.enums.PaymentStatus;
import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.jobs.ExponentialBackoff;
import com.example.narayan.paymentsystem.queue.jobs.JobResult;
import com.example.narayan.paymentsystem.queue.jobs.JobStatus;
import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.example.narayan.paymentsystem.repository.PaymentRepository;
import com.example.narayan.paymentsystem.service.PaymentGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.time.LocalDateTime;

@Component
public class PaymentJobProcessor implements JobProcessor<PaymentJob> {

    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    PaymentGatewayService paymentGatewayService;
    @Autowired
    JobQueue jobQueue;
    @Autowired
    JedisPool jedisPool;

    public void startProcessor() {
        Thread worker = new Thread(() -> {
            while (true) {
                try (var jedis = jedisPool.getResource()) {
                    jedis.ping(); // check Redis
                } catch (JedisConnectionException e) {
                    System.err.println("⚠️ Redis down, retrying in 2s");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public JobResult process(PaymentJob job) {
        Payment payment = paymentRepository.findById(job.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found for job"));

        try {
            paymentGatewayService.processPayment(payment.getId());
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setCompletedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            return new JobResult(JobStatus.COMPLETED, "Payment processed successfully");
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            payment.setCompletedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            ExponentialBackoff exponentialBackoff = new ExponentialBackoff(100,5000,2.0);

            return new JobResult(JobStatus.FAILED, "Payment failed: " + e.getMessage());
        }
    }
}
