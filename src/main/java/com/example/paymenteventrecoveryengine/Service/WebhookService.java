package com.example.paymenteventrecoveryengine.Service;

import com.example.paymenteventrecoveryengine.dto.PaymentRequest;
import com.example.paymenteventrecoveryengine.entity.AuditLog;
import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.exception.DuplicatePaymentException;
import com.example.paymenteventrecoveryengine.repository.AuditLogRepository;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
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
        log.info("Received payment event with paymentId={}", paymentRequest.paymentId());


        if (paymentRepository.existsByPaymentId(paymentRequest.paymentId())) {
            log.warn("Duplicate payment received. paymentId={}", paymentRequest.paymentId());
            throw new DuplicatePaymentException(
                    "Payment already exists with id: "
                            + paymentRequest.paymentId()
            );
        }


        PaymentEvent paymentEvent = new PaymentEvent();

        paymentEvent.setPaymentId(paymentRequest.paymentId());
        paymentEvent.setOrderId(paymentRequest.orderId());
        paymentEvent.setAmount(paymentRequest.amount());
        paymentEvent.setCurrency(paymentRequest.currency());

        paymentEvent.setStatus(PaymentStatus.PENDING);
        paymentEvent.setRetryCount(0);
        paymentEvent.setFailureReason(null);


        paymentRepository.save(paymentEvent);

        AuditLog auditLog = new AuditLog();

        auditLog.setPaymentId(paymentEvent.getPaymentId());
        auditLog.setAction("PAYMENT_RECEIVED");
        auditLog.setMessage("Payment webhook received successfully");

        auditLogRepository.save(auditLog);
        log.info("Payment {} saved successfully.", paymentRequest.paymentId());
        log.info("Sending payment {} to DeliveryService.", paymentRequest.paymentId());
        deliveryService.deliver(paymentEvent);


    }
}