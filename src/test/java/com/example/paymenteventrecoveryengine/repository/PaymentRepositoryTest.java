
package com.example.paymenteventrecoveryengine.repository;

import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PaymentEvent createPayment(
            String paymentId,
            PaymentStatus status,
            LocalDateTime nextRetryAt) {

        PaymentEvent payment = new PaymentEvent();

        payment.setPaymentId(paymentId);
        payment.setOrderId("ORD-" + paymentId);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("INR");
        payment.setStatus(status);
        payment.setRetryCount(0);
        payment.setNextRetryAt(nextRetryAt);

        return payment;
    }

    @Test
    void findDuePayments_returnsOnlyEligiblePayments() {
        LocalDateTime now = LocalDateTime.now();

        PaymentEvent duePayment = createPayment(
                "PAY-TEST-DUE",
                PaymentStatus.FAILED,
                now.minusMinutes(2)
        );

        PaymentEvent futurePayment = createPayment(
                "PAY-TEST-FUTURE",
                PaymentStatus.FAILED,
                now.plusMinutes(30)
        );

        PaymentEvent deliveredPayment = createPayment(
                "PAY-TEST-DELIVERED",
                PaymentStatus.DELIVERED,
                now.minusMinutes(2)
        );

        paymentRepository.saveAllAndFlush(
                List.of(duePayment, futurePayment, deliveredPayment)
        );

        List<PaymentEvent> result =
                paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                        PaymentStatus.FAILED,
                        now
                );

        assertEquals(1, result.size());
        assertEquals("PAY-TEST-DUE", result.get(0).getPaymentId());
    }

    @Test
    void claimPayment_whenStatusIsFailed_updatesStatusToProcessing() {
        LocalDateTime now = LocalDateTime.now();

        PaymentEvent payment = createPayment(
                "PAY-TEST-CLAIM",
                PaymentStatus.FAILED,
                now.minusMinutes(1)
        );

        payment = paymentRepository.saveAndFlush(payment);
        Long paymentId = payment.getId();

        int updatedRows = paymentRepository.claimPayment(
                paymentId,
                PaymentStatus.PROCESSING,
                PaymentStatus.FAILED,
                now
        );

        entityManager.clear();

        PaymentEvent updatedPayment =
                paymentRepository.findById(paymentId).orElseThrow();

        assertEquals(1, updatedRows);
        assertEquals(PaymentStatus.PROCESSING, updatedPayment.getStatus());
    }

    @Test
    void claimPayment_whenStatusIsNotFailed_updatesZeroRows() {
        LocalDateTime now = LocalDateTime.now();

        PaymentEvent payment = createPayment(
                "PAY-TEST-ALREADY-PROCESSING",
                PaymentStatus.PROCESSING,
                now.minusMinutes(1)
        );

        payment = paymentRepository.saveAndFlush(payment);
        Long paymentId = payment.getId();

        int updatedRows = paymentRepository.claimPayment(
                paymentId,
                PaymentStatus.PROCESSING,
                PaymentStatus.FAILED,
                now
        );

        entityManager.clear();

        PaymentEvent unchangedPayment =
                paymentRepository.findById(paymentId).orElseThrow();

        assertEquals(0, updatedRows);
        assertEquals(
                PaymentStatus.PROCESSING,
                unchangedPayment.getStatus()
        );
    }

    @Test
    void recoverStaleProcessingPayment_whenOneStalePayment_shouldMarkItFailed() {
        LocalDateTime now = LocalDateTime.now();

        PaymentEvent payment = createPayment(
                "PAY-TEST-STALE-001",
                PaymentStatus.PROCESSING,
                now.minusMinutes(4)
        );

        payment = paymentRepository.saveAndFlush(payment);
        Long paymentId = payment.getId();

        // Simulate a payment abandoned four minutes ago.
        jdbcTemplate.update(
                "UPDATE payment_events SET updated_at = ? WHERE id = ?",
                now.minusMinutes(4),
                paymentId
        );

        int staleCount = paymentRepository.recoverStaleProcessingPayments(
                PaymentStatus.PROCESSING,
                PaymentStatus.FAILED,
                now.minusMinutes(3),
                now
        );

        entityManager.clear();

        PaymentEvent changedPayment =
                paymentRepository.findById(paymentId).orElseThrow();

        assertEquals(1, staleCount);
        assertEquals(PaymentStatus.FAILED, changedPayment.getStatus());
        assertEquals(now, changedPayment.getNextRetryAt());
        assertEquals(
                "Recovered stale PROCESSING payment",
                changedPayment.getFailureReason()
        );
    }

    @Test
    void recoverStaleProcessingPayment_whenNoStalePayment_shouldLeaveItProcessing() {
        LocalDateTime now = LocalDateTime.now();

        PaymentEvent payment = createPayment(
                "PAY-TEST-RECENT-001",
                PaymentStatus.PROCESSING,
                now.minusMinutes(2)
        );

        payment = paymentRepository.saveAndFlush(payment);
        Long paymentId = payment.getId();

        int staleCount = paymentRepository.recoverStaleProcessingPayments(
                PaymentStatus.PROCESSING,
                PaymentStatus.FAILED,
                now.minusMinutes(3),
                now
        );

        entityManager.clear();

        PaymentEvent unchangedPayment =
                paymentRepository.findById(paymentId).orElseThrow();

        assertEquals(0, staleCount);
        assertEquals(
                PaymentStatus.PROCESSING,
                unchangedPayment.getStatus()
        );
    }

    @Test
    void recoverStaleProcessingPayments_whenMultipleStalePayments_shouldRecoverAll() {
        LocalDateTime now = LocalDateTime.now();

        PaymentEvent payment1 = createPayment(
                "PAY-TEST-MULTI-STALE-001",
                PaymentStatus.PROCESSING,
                now.minusMinutes(5)
        );

        PaymentEvent payment2 = createPayment(
                "PAY-TEST-MULTI-STALE-002",
                PaymentStatus.PROCESSING,
                now.minusMinutes(4)
        );

        paymentRepository.saveAllAndFlush(List.of(payment1, payment2));

        // Simulate both payments being abandoned four minutes ago.
        jdbcTemplate.update(
                "UPDATE payment_events SET updated_at = ? WHERE payment_id = ?",
                now.minusMinutes(4),
                payment1.getPaymentId()
        );

        jdbcTemplate.update(
                "UPDATE payment_events SET updated_at = ? WHERE payment_id = ?",
                now.minusMinutes(4),
                payment2.getPaymentId()
        );

        int recoveredCount = paymentRepository.recoverStaleProcessingPayments(
                PaymentStatus.PROCESSING,
                PaymentStatus.FAILED,
                now.minusMinutes(3),
                now
        );

        entityManager.clear();

        PaymentEvent recovered1 = paymentRepository
                .findById(payment1.getId())
                .orElseThrow();

        PaymentEvent recovered2 = paymentRepository
                .findById(payment2.getId())
                .orElseThrow();

        assertEquals(2, recoveredCount);
        assertEquals(PaymentStatus.FAILED, recovered1.getStatus());
        assertEquals(PaymentStatus.FAILED, recovered2.getStatus());
    }
}
