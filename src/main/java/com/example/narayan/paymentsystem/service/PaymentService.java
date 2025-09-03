package com.example.narayan.paymentsystem.service;

import com.example.narayan.paymentsystem.dto.PaymentRequestDto;
import com.example.narayan.paymentsystem.dto.PaymentResponseDto;
import com.example.narayan.paymentsystem.exception.PaymentNotFound;
import com.example.narayan.paymentsystem.model.Payment;
import com.example.narayan.paymentsystem.model.enums.PaymentMethodType;
import com.example.narayan.paymentsystem.model.enums.PaymentStatus;
import com.example.narayan.paymentsystem.queue.JobQueue;
import com.example.narayan.paymentsystem.queue.RedisPriorityJobQueue;
import com.example.narayan.paymentsystem.queue.jobs.PaymentJob;
import com.example.narayan.paymentsystem.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.*;

@Service
public class PaymentService {

    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    PaymentGatewayService paymentGatewayService;
    @Autowired
    CardValidationService cardValidationService;
    @Autowired
    UPIValidationService upiValidationService;
    @Autowired
    RedisPriorityJobQueue jobQueue;

    //Initiate the payment and save in the db
    public PaymentResponseDto initiatePayment(PaymentRequestDto paymentRequestDto){

        //Identify the payment type
        if(paymentRequestDto.getPaymentMethodType() == PaymentMethodType.CREDIT_CARD){
            cardValidationService.validate(paymentRequestDto);
        }
        else if(paymentRequestDto.getPaymentMethodType() == PaymentMethodType.UPI){
            upiValidationService.validateUPIId(paymentRequestDto);
        }

        Payment exist = paymentRepository.findByIdempotencyKey(paymentRequestDto.getIdempotency_key()).orElse(null);
        if(exist != null){
            return mapToResponse(exist);
        }

        Payment payment = new Payment();
        payment.setAmount(paymentRequestDto.getAmount());
        payment.setCurrency(paymentRequestDto.getCurrency());
        payment.setPaymentMethodId(paymentRequestDto.getPaymentMethod_id());
        payment.setPaymentMethodType(paymentRequestDto.getPaymentMethodType());

        if (paymentRequestDto.getIdempotency_key() != null && !paymentRequestDto.getIdempotency_key().isBlank()) {
            payment.setIdempotencyKey(paymentRequestDto.getIdempotency_key());
        } else {
            payment.setIdempotencyKey(UUID.randomUUID().toString());
        }
        payment.setUser_id(UUID.randomUUID());  // from authenticated session
        payment.setStatus(PaymentStatus.PENDING);

        Payment saved = paymentRepository.save(payment);

        try {
            //Fast path - try gateway call with timeout
            boolean processed = tryImmediateProcessing(saved);
            if(!processed) {
                // Check DB status before enqueuing
                Payment fresh = paymentRepository.findById(saved.getId()).orElseThrow();
                if (fresh.getStatus() != PaymentStatus.SUCCESS) {
                    fresh.setStatus(PaymentStatus.PROCESSING);
                    paymentRepository.save(fresh);
                    jobQueue.enqueue(PaymentJob.of(fresh.getId(), fresh.getAmount().intValue()));
                }
            }
        }
        catch (Exception e){
            saved.setStatus(PaymentStatus.PROCESSING);
            paymentRepository.save(saved);
            jobQueue.enqueue(PaymentJob.of(saved.getId(),saved.getAmount().intValue()));
            return mapToResponse(saved);
        }
        return mapToResponse(saved);
    }

    public PaymentResponseDto mapToResponse(Payment payment){
        PaymentResponseDto paymentResponseDto = new PaymentResponseDto();

        paymentResponseDto.setPaymentId(payment.getId());
        paymentResponseDto.setCompletedAt(payment.getCompletedAt());
        paymentResponseDto.setStatus(payment.getStatus());
        paymentResponseDto.setFailureReason(payment.getFailureReason());
        paymentResponseDto.setAmount(payment.getAmount());
        paymentResponseDto.setCurrency(payment.getCurrency());
        paymentResponseDto.setGatewayTransactionId(payment.getGatewayTransactionId());

        paymentResponseDto.setMessage(buildPaymentMessage(payment));

        return paymentResponseDto;
    }
    public boolean tryImmediateProcessing(Payment payment){
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->{
            try{
                paymentGatewayService.processPayment(payment.getId());
                payment.setStatus(PaymentStatus.SUCCESS);
                paymentRepository.save(payment);
                return true;
            }
            catch (Exception e){
                return false;
            }
        }, executor);

        try {
            return future.get(2, TimeUnit.SECONDS);
        }
        catch (TimeoutException e){
            future.cancel(true);
            return false;
        }
        catch (Exception e) {
            return false;
        }
        finally {
            executor.shutdown();
        }
    }

    public PaymentResponseDto getPaymentById(UUID id) {
        Payment payment =  paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFound("Payment not found"));

        PaymentResponseDto paymentResponseDto = new PaymentResponseDto();
        paymentResponseDto.setPaymentId(payment.getId());
        paymentResponseDto.setStatus(payment.getStatus());
        paymentResponseDto.setMessage(buildPaymentMessage(payment));
        paymentResponseDto.setFailureReason(payment.getFailureReason());
        paymentResponseDto.setCompletedAt(payment.getCompletedAt());
        paymentResponseDto.setAmount(payment.getAmount());
        paymentResponseDto.setCurrency(payment.getCurrency());

        return paymentResponseDto;
    }

    public String buildPaymentMessage(Payment payment) {

        switch (payment.getStatus()) {
            case PENDING:
                return ("Payment is pending. Awaiting processing.");
            case PROCESSING:
                return ("Payment is currently being processed.");
            case SUCCESS:
                return ("Payment was successful.");
            case FAILED:
                return (payment.getFailureReason() != null ? payment.getFailureReason() : "Payment failed.");
            case CANCELLED:
                return ("Payment was cancelled.");
            default:
                return ("Unknown payment status.");
        }
    }
}
