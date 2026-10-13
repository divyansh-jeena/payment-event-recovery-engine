
package com.example.paymenteventrecoveryengine.repository;

import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

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


    // Test 1: Only failed payments whose retry time has arrived
    // should be returned.
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

        paymentRepository.saveAll(
                List.of(duePayment, futurePayment, deliveredPayment)
        );

        paymentRepository.flush();

        List<PaymentEvent> result =
                paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                        PaymentStatus.FAILED,
                        now
                );

        assertEquals(1, result.size());
        assertEquals(
                "PAY-TEST-DUE",
                result.get(0).getPaymentId()
        );
    }


    // Test 2: A failed payment can be claimed.
    @Test
    void claimPayment_whenStatusIsFailed_updatesStatusToProcessing() {

        PaymentEvent payment = createPayment(
                "PAY-TEST-CLAIM",
                PaymentStatus.FAILED,
                LocalDateTime.now().minusMinutes(1)
        );

        payment = paymentRepository.saveAndFlush(payment);

        Long paymentId = payment.getId();

        int updatedRows = paymentRepository.claimPayment(
                paymentId,
                PaymentStatus.PROCESSING,
                PaymentStatus.FAILED
        );

        entityManager.flush();
        entityManager.clear();

        PaymentEvent updatedPayment =
                paymentRepository.findById(paymentId).orElseThrow();

        assertEquals(1, updatedRows);
        assertEquals(
                PaymentStatus.PROCESSING,
                updatedPayment.getStatus()
        );
    }


    // Test 3: A payment that is no longer FAILED cannot be claimed again.
    @Test
    void claimPayment_whenStatusIsNotFailed_updatesZeroRows() {

        PaymentEvent payment = createPayment(
                "PAY-TEST-ALREADY-PROCESSING",
                PaymentStatus.PROCESSING,
                LocalDateTime.now().minusMinutes(1)
        );

        payment = paymentRepository.saveAndFlush(payment);

        Long paymentId = payment.getId();

        int updatedRows = paymentRepository.claimPayment(
                paymentId,
                PaymentStatus.PROCESSING,
                PaymentStatus.FAILED
        );

        entityManager.flush();
        entityManager.clear();

        PaymentEvent unchangedPayment =
                paymentRepository.findById(paymentId).orElseThrow();

        assertEquals(0, updatedRows);
        assertEquals(
                PaymentStatus.PROCESSING,
                unchangedPayment.getStatus()
        );
    }
}
