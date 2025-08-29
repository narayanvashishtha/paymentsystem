package com.example.narayan.paymentsystem.service;

import com.example.narayan.paymentsystem.dto.PaymentRequestDto;
import com.example.narayan.paymentsystem.exception.InvalidPaymentRequest;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

@Service
public class CardValidationService {

    public void validate(PaymentRequestDto cardDto) {
        if(!validateCardNumber(cardDto.getCardNumber())){
            throw new InvalidPaymentRequest("Invalid card number");
        }
        if(!validExpiryDate(cardDto.getExpiryMonth(), cardDto.getExpiryYear())){
            throw new InvalidPaymentRequest("Invalid expiry date format (MM/YY)");
        }
        if(!validateCVV(cardDto.getCardNumber(), cardDto.getCvv())){
            throw new InvalidPaymentRequest("Invalid CVV");
        }
    }

    public boolean validateCardNumber(String cardNumber){
        if(cardNumber.isEmpty()){
            return false;
        }
        String digits = cardNumber.replaceAll("\\D", "");
        return isValidLuhn(digits);
    }

    private boolean isValidLuhn(String cardNo) {
        int nDigits = cardNo.length();

        int nSum = 0;
        boolean isSecond = false;
        for (int i = nDigits - 1; i >= 0; i--)
        {

            int d = cardNo.charAt(i) - '0';

            if (isSecond)
                d = d * 2;

            // We add two digits to handle cases that make two digits after doubling
            nSum += d / 10;
            nSum += d % 10;

            isSecond = !isSecond;
        }
        return (nSum % 10 == 0);
    }

    private boolean validExpiryDate(int month, int year){
        if(month < 1 || month > 12){
            return false;
        }
        //Current year+month
        YearMonth current = YearMonth.now();
        if(year < 100){
            year += 2000;
        }

        YearMonth expiry = YearMonth.of(year, month);
        //Card is valid if expiry is this month or later
        return !expiry.isBefore(current);
    }

    private boolean validateCVV(String cardNumber, String cvv){
        if(cvv == null || cvv.isEmpty()){
            return false;
        }

        if(!cvv.matches("\\d+")){
            return false;
        }

        boolean isAmex = cardNumber != null && (cardNumber.startsWith("34") || cardNumber.startsWith("37"));

        if(isAmex){
            return cvv.length() == 4;
        }
        else{
            return cvv.length() == 3;
        }
    }
}
