package com.example.narayan.paymentsystem.service;

import com.example.narayan.paymentsystem.exception.PaymentNotFound;
import com.example.narayan.paymentsystem.model.Payment;
import com.example.narayan.paymentsystem.model.enums.PaymentStatus;
import com.example.narayan.paymentsystem.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

@Component
public class PaymentGatewayService {

    @Autowired
    PaymentRepository paymentRepository;

    //Simulate a payment gateway call
    public Payment processPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFound("Payment not found"));

        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        Random random = new Random();
        //Randomly succeed or fail
        boolean success = random.nextBoolean();

        if(success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setGatewayTransactionId("TXN" + System.currentTimeMillis());
        } else {
            payment.setStatus(PaymentStatus.FAILED);

            switch (payment.getPaymentMethodType()) {
                case CREDIT_CARD:
                    payment.setFailureReason("Card declined or insufficient funds");
                    break;
                case UPI:
                    payment.setFailureReason("UPI app not responding or invalid VPA");
                    break;
                default:
                    payment.setFailureReason("Unknown payment error");
            }
        }
        payment.setCompletedAt(java.time.LocalDateTime.now());
        paymentRepository.save(payment);
        return payment;
    }
}

