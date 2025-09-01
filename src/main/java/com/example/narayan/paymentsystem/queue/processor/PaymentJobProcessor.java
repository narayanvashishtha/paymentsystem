package com.example.narayan.paymentsystem.queue.processor;

import com.example.narayan.paymentsystem.model.Payment;
import com.example.narayan.paymentsystem.model.enums.PaymentStatus;
import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.example.narayan.paymentsystem.repository.PaymentRepository;
import com.example.narayan.paymentsystem.service.PaymentGatewayService;
import org.springframework.beans.factory.annotation.Autowired;

public class PaymentJobProcessor implements JobProcessor<PaymentJob> {

    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    PaymentGatewayService paymentGatewayService;
    @Autowired
    JobQueue jobQueue;

    public void startProcessor() {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    PaymentJob job = jobQueue.dequeue();
                    process(job);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void process(PaymentJob job) {
        Payment payment = paymentRepository.findById(job.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found for job"));

        try {
            paymentGatewayService.processPayment(payment.getId());
            payment.setStatus(PaymentStatus.SUCCESS);
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
        }
        paymentRepository.save(payment);
    }
}
