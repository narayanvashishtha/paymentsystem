package com.example.narayan.paymentsystem.controller;

import com.example.narayan.paymentsystem.dto.PaymentRequestDto;
import com.example.narayan.paymentsystem.dto.PaymentResponseDto;
import com.example.narayan.paymentsystem.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    @Autowired
    PaymentService paymentService;

    @PostMapping("/payments")
    public ResponseEntity<PaymentResponseDto> makepayment(@Valid @RequestBody PaymentRequestDto paymentRequestDto) {
        PaymentResponseDto responseDto = paymentService.initiatePayment(paymentRequestDto);

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<PaymentResponseDto> getstatus(@PathVariable UUID paymentId) {
        PaymentResponseDto response = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(response);
    }
}
