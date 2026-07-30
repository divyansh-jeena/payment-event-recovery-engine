package com.example.paymenteventrecoveryengine.enums;
public enum AuditAction {
    PAYMENT_RECEIVED,
    DELIVERY_ATTEMPTED,
    DELIVERY_SUCCESSFUL,
    DELIVERY_FAILED,
    RETRY_STARTED,
    RETRY_SUCCESSFUL,
    RETRY_FAILED,
    MOVED_TO_DLQ,
    MANUAL_RETRY_STARTED
}
