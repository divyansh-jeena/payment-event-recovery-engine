
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
    private final PaymentClaimService paymentClaimService;
    private final DeliveryService deliveryService;

    @Scheduled(cron = "0 * * * * *")

    public void retryFailedPayments() {

        log.info("Retry Scheduler started.");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(3);

        // Step 1: Recover stale PROCESSING payments.
        int recovered =
                paymentRepository.recoverStaleProcessingPayments(
                        PaymentStatus.PROCESSING,
                        PaymentStatus.FAILED,
                        cutoff,
                        now
                );

        if (recovered > 0) {
            log.warn(
                    "Recovered {} stale PROCESSING payment(s).",
                    recovered
            );
        }

        // Step 2: Load payments ready for retry.
        List<PaymentEvent> payments =
                paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                        PaymentStatus.FAILED,
                        now
                );

        log.info("{} payment(s) ready for retry.", payments.size());

        // Step 3: Claim each payment atomically before delivery.
        for (PaymentEvent payment : payments) {
            try {
                boolean claimed = paymentClaimService.claim(
                        payment.getId(),
                        PaymentStatus.FAILED
                );

                if (!claimed) {
                    log.info(
                            "Skipping payment {}; it is no longer eligible.",
                            payment.getPaymentId()
                    );
                    continue;
                }

                // Reload after the bulk claim so the entity has
                // the current PROCESSING status and updatedAt.
                PaymentEvent claimedPayment =
                        paymentRepository.findById(payment.getId())
                                .orElseThrow();

                log.info(
                        "Retrying payment {}",
                        claimedPayment.getPaymentId()
                );

                deliveryService.deliver(claimedPayment);

            } catch (Exception ex) {
                log.error(
                        "Retry failed for payment {}",
                        payment.getPaymentId(),
                        ex
                );
            }
        }
    }
}
