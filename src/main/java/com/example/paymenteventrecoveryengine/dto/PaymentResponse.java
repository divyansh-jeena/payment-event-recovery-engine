package com.example.paymenteventrecoveryengine.dto;


import java.math.BigDecimal;

public record PaymentResponse(
        String paymentId,
        String orderId,
        BigDecimal amount,
        String currency,
        String status,
        Integer retryCount,
        String failureReason
) {
}
