package com.example.paymenteventrecoveryengine.Service;

import com.example.paymenteventrecoveryengine.Configuration.RetryProperties;
import com.example.paymenteventrecoveryengine.client.OrderServiceClient;
import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.AuditAction;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class DeliveryService {

    private final RetryProperties retryProperties;
    private final AuditService auditService;
    private final PaymentRepository paymentRepository;
    private final OrderServiceClient orderServiceClient;

    public DeliveryService(
            AuditService auditService,
            PaymentRepository paymentRepository,
            RetryProperties retryProperties,
            OrderServiceClient orderServiceClient) {

        this.auditService = auditService;
        this.paymentRepository = paymentRepository;
        this.retryProperties = retryProperties;
        this.orderServiceClient = orderServiceClient;
    }

    public void deliver(PaymentEvent paymentEvent) {

        log.info(
                "Starting delivery for payment {}",
                paymentEvent.getPaymentId()
        );

        auditService.createAuditLog(
                paymentEvent.getPaymentId(),
                AuditAction.DELIVERY_ATTEMPTED.name(),
                "Attempting delivery to Order Service."
        );

        try {

            // Call the actual downstream Mock Order Service
            orderServiceClient.deliver(paymentEvent);

            // If no exception occurred, delivery was successful
            markDelivered(paymentEvent);

        } catch (Exception ex) {

            log.error(
                    "Delivery failed for payment {}. Reason={}",
                    paymentEvent.getPaymentId(),
                    ex.getMessage()
            );

            markFailed(
                    paymentEvent,
                    ex.getMessage()
            );
        }
    }

    private void markDelivered(PaymentEvent paymentEvent) {

        paymentEvent.setStatus(PaymentStatus.DELIVERED);
        paymentEvent.setFailureReason(null);
        paymentEvent.setNextRetryAt(null);
        paymentEvent.setRetryCount(0);

        paymentRepository.save(paymentEvent);

        auditService.createAuditLog(
                paymentEvent.getPaymentId(),
                AuditAction.DELIVERY_SUCCESSFUL.name(),
                "Payment delivered successfully."
        );

        log.info(
                "Payment {} delivered successfully.",
                paymentEvent.getPaymentId()
        );
    }

    private void markFailed(
            PaymentEvent paymentEvent,
            String reason) {

        // Make sure we have a useful failure reason
        if (reason == null || reason.isBlank()) {
            reason = "Unknown downstream delivery failure";
        }

        paymentEvent.setFailureReason(reason);

        // Increment retry count
        paymentEvent.setRetryCount(
                paymentEvent.getRetryCount() + 1
        );

        int retryCount = paymentEvent.getRetryCount();


        if (retryCount >= retryProperties.getMaxAttempts()) {

            paymentEvent.setStatus(PaymentStatus.DEAD);
            paymentEvent.setNextRetryAt(null);

            paymentRepository.save(paymentEvent);

            auditService.createAuditLog(
                    paymentEvent.getPaymentId(),
                    AuditAction.MOVED_TO_DLQ.name(),
                    "Maximum retry limit reached."
            );

            log.error(
                    "Payment {} moved to DEAD after {} retries. Reason={}",
                    paymentEvent.getPaymentId(),
                    retryCount,
                    reason
            );

            return;
        }

        /*
         * Retry is still possible.
         */
        paymentEvent.setStatus(PaymentStatus.FAILED);

        /*
         * Exponential backoff:
         *
         * delay = initialDelay × multiplier^(retryCount - 1)
         */
        long delaySeconds = (long) (
                retryProperties
                        .getInitialDelay()
                        .toSeconds()
                        *
                        Math.pow(
                                retryProperties.getMultiplier(),
                                retryCount - 1
                        )
        );

        /*
         * Prevent the delay from exceeding maxDelay.
         */
        delaySeconds = Math.min(
                delaySeconds,
                retryProperties.getMaxDelay().toSeconds()
        );

        paymentEvent.setNextRetryAt(
                LocalDateTime.now()
                        .plusSeconds(delaySeconds)
        );

        paymentRepository.save(paymentEvent);

        auditService.createAuditLog(
                paymentEvent.getPaymentId(),
                AuditAction.DELIVERY_FAILED.name(),
                reason
        );

        log.warn(
                "Payment {} delivery failed. Retry {} scheduled at {}",
                paymentEvent.getPaymentId(),
                retryCount,
                paymentEvent.getNextRetryAt()
        );
    }
}