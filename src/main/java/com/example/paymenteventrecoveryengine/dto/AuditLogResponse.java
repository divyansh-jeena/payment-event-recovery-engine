package com.example.paymenteventrecoveryengine.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        String action,
        String message,
        LocalDateTime createdAt
) {
}