package com.example.narayan.paymentsystem.service;

import com.example.narayan.paymentsystem.dto.PaymentRequestDto;
import com.example.narayan.paymentsystem.exception.InvalidPaymentRequest;
import org.springframework.stereotype.Service;

@Service
public class UPIValidationService {

    public void validateUPIId(PaymentRequestDto upiDto){
        String upiId = upiDto.getUpiId();
        if (upiId == null || upiId.isBlank() || !upiId.contains("@")) {
            throw new InvalidPaymentRequest("Invalid UPI ID format");
        }

        String[] part = upiId.split("@");
        String username = part[0];
        String handle = part[1];

        if(username.length() < 3 || username.length() > 15){
            throw new InvalidPaymentRequest("Invalid UPI username");
        }
        if (!username.matches("^[a-zA-Z0-9-]+$")){
            throw new InvalidPaymentRequest("UPI username must contain only letters and digits");
        }

        if (handle.length() < 2 || handle.length() > 15){
            throw new InvalidPaymentRequest("Invalid UPI handle");
        }
        if(!handle.matches("^[a-zA-Z]+$")){
            throw new InvalidPaymentRequest("UPI handle must contain only letters");
        }
    }
}
