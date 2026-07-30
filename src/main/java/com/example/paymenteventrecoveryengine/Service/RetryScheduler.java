package com.example.paymenteventrecoveryengine.Service;


import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

    @Service
    @Slf4j
    @RequiredArgsConstructor
    public class RetryScheduler {

        private final PaymentRepository paymentRepository;
        private final DeliveryService deliveryService;

        @Scheduled(cron = "0 * * * * *")
        public void retryFailedPayments() {

            log.info("Retry Scheduler started.");

            LocalDateTime now = LocalDateTime.now();

            List<PaymentEvent> payments =
                    paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                            PaymentStatus.FAILED,
                            now
                    );

            log.info("{} payment(s) ready for retry.", payments.size());

            for (PaymentEvent payment : payments) {
                try {
                    log.info("Retrying payment {}", payment.getPaymentId());
                    deliveryService.deliver(payment);
                } catch (Exception ex) {
                    log.error(
                            "Retry failed for payment {}",
                            payment.getPaymentId(),
                            ex
                    );
                }
            }

            log.info("Retry Scheduler completed.");
        }
    }


