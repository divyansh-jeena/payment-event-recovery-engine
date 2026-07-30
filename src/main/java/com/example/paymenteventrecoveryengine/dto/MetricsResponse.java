package com.example.paymenteventrecoveryengine.dto;

public record MetricsResponse(
        long totalPayments,
        long pending,
        long failed,
        long delivered,
        long dead
) {
}