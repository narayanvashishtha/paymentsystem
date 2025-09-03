package com.example.narayan.paymentsystem;

import com.example.narayan.paymentsystem.queue.processor.PaymentJobProcessor;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentsystemApplication {

	@Autowired
	PaymentJobProcessor paymentJobProcessor;

	public static void main(String[] args) {
		SpringApplication.run(PaymentsystemApplication.class, args);
	}

	@PostConstruct
	public void startWorkers() {
		paymentJobProcessor.startProcessor();
		System.out.println("🚀 Payment job processor started!");
	}
}
