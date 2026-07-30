package com.example.paymenteventrecoveryengine.Service;


import com.example.paymenteventrecoveryengine.entity.AuditLog;
import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.AuditAction;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.repository.AuditLogRepository;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@Slf4j

    public class DeliveryService {
    private final Random random = new Random();
    private static  final   int MAX_RETRY_COUNT=5;
    private  final AuditService auditService ;
    private final PaymentRepository paymentRepository;

    public DeliveryService( AuditService auditService, PaymentRepository paymentRepository) {
        this.auditService = auditService;

        this.paymentRepository = paymentRepository;
    }
    @Transactional
    public void deliver(PaymentEvent paymentEvent) {
        log.info("Starting delivery for payment {}", paymentEvent.getPaymentId());
       auditService.createAuditLog(
                paymentEvent.getPaymentId(),
                AuditAction.DELIVERY_ATTEMPTED.name(),
                "Attempting delivery to Order Service."
        );

        boolean success = simulateOrderService();

        if (success) {
            markDelivered(paymentEvent);
        } else {
            markFailed(paymentEvent, "Order Service Timeout");
        }



        }

        private boolean simulateOrderService() {

       return  random.nextBoolean();

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
            log.info("Payment {} delivered successfully.", paymentEvent.getPaymentId());


        }

        private void markFailed(PaymentEvent paymentEvent, String reason) {


            paymentEvent.setFailureReason(reason);
            paymentEvent.setRetryCount( paymentEvent.getRetryCount()+1);
            int retryCount=paymentEvent.getRetryCount();
            if (retryCount >= MAX_RETRY_COUNT) {

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

            paymentEvent.setStatus(PaymentStatus.FAILED);

            long delay = (long) Math.pow(2, retryCount - 1);
            paymentEvent.setNextRetryAt(LocalDateTime.now().plusMinutes(delay)
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


