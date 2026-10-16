package com.example.paymenteventrecoveryengine.Service;

import com.example.paymenteventrecoveryengine.dto.PaymentRequest;
import com.example.paymenteventrecoveryengine.entity.AuditLog;
import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.exception.DuplicatePaymentException;
import com.example.paymenteventrecoveryengine.repository.AuditLogRepository;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WebhookService {
private final DeliveryService deliveryService;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;

    public WebhookService(DeliveryService deliveryService, PaymentRepository paymentRepository, AuditLogRepository auditLogRepository) {
        this.deliveryService = deliveryService;
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
    }


    public void processPayment(PaymentRequest paymentRequest) {
        String paymentId = paymentRequest.paymentId();

        log.info("Received payment event with paymentId={}", paymentId);

        // Check for a duplicate that already exists.
        if (paymentRepository.existsByPaymentId(paymentId)) {
            log.warn("Duplicate payment received. paymentId={}", paymentId);
            throw new DuplicatePaymentException(
                    "Payment already exists with id: " + paymentId
            );
        }

        PaymentEvent paymentEvent = new PaymentEvent();
        paymentEvent.setPaymentId(paymentId);
        paymentEvent.setOrderId(paymentRequest.orderId());
        paymentEvent.setAmount(paymentRequest.amount());
        paymentEvent.setCurrency(paymentRequest.currency());
        paymentEvent.setStatus(PaymentStatus.PENDING);
        paymentEvent.setRetryCount(0);
        paymentEvent.setFailureReason(null);

        // Save immediately so database constraint errors happen here.
        try {
            paymentRepository.saveAndFlush(paymentEvent);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request may have inserted the same payment ID.
            if (paymentRepository.existsByPaymentId(paymentId)) {
                log.warn("Concurrent duplicate payment received. paymentId={}",
                        paymentId);

                throw new DuplicatePaymentException(
                        "Payment already exists with id: " + paymentId
                );
            }

            // This was a different database constraint violation.
            throw ex;
        }

        // Only a successfully saved payment reaches this point.
        AuditLog auditLog = new AuditLog();
        auditLog.setPaymentId(paymentId);
        auditLog.setAction("PAYMENT_RECEIVED");
        auditLog.setMessage("Payment webhook received successfully");
        auditLogRepository.save(auditLog);

        log.info("Payment {} saved successfully.", paymentId);
        log.info("Sending payment {} to DeliveryService.", paymentId);

        deliveryService.deliver(paymentEvent);
    }

}