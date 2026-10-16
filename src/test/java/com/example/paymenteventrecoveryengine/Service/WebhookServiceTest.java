package com.example.paymenteventrecoveryengine.Service;


import com.example.paymenteventrecoveryengine.dto.PaymentRequest;
import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.exception.DuplicatePaymentException;
import com.example.paymenteventrecoveryengine.repository.AuditLogRepository;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private WebhookService webhookService;

    private PaymentRequest paymentRequest;

    @BeforeEach
    void setUp() {
        paymentRequest = new PaymentRequest(
                "PAY-TEST-001",
                "ORD-TEST-001",
                new BigDecimal("500.00"),
                "INR"
        );
    }

    @Test
    void processPaymentShouldSaveAuditLogAndDeliverPayment() {
        when(paymentRepository.existsByPaymentId("PAY-TEST-001"))
                .thenReturn(false);

        when(paymentRepository.saveAndFlush(any(PaymentEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        webhookService.processPayment(paymentRequest);

        verify(paymentRepository).saveAndFlush(any(PaymentEvent.class));
        verify(auditLogRepository).save(any());
        verify(deliveryService).deliver(any(PaymentEvent.class));
    }

    @Test
    void processPaymentShouldRejectExistingDuplicate() {
        when(paymentRepository.existsByPaymentId("PAY-TEST-001"))
                .thenReturn(true);

        assertThrows(
                DuplicatePaymentException.class,
                () -> webhookService.processPayment(paymentRequest)
        );

        verify(paymentRepository, never()).saveAndFlush(any());
        verify(auditLogRepository, never()).save(any());
        verify(deliveryService, never()).deliver(any());
    }

    @Test
    void processPaymentShouldRejectConcurrentDuplicateDetectedByDatabase() {
        when(paymentRepository.existsByPaymentId("PAY-TEST-001"))
                .thenReturn(false, true);

        when(paymentRepository.saveAndFlush(any(PaymentEvent.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        assertThrows(
                DuplicatePaymentException.class,
                () -> webhookService.processPayment(paymentRequest)
        );

        verify(paymentRepository).saveAndFlush(any(PaymentEvent.class));
        verify(auditLogRepository, never()).save(any());
        verify(deliveryService, never()).deliver(any());
    }

    @Test
    void processPaymentShouldRethrowUnrelatedDatabaseIntegrityFailure() {
        when(paymentRepository.existsByPaymentId("PAY-TEST-001"))
                .thenReturn(false, false);

        when(paymentRepository.saveAndFlush(any(PaymentEvent.class)))
                .thenThrow(new DataIntegrityViolationException("Some other database constraint"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> webhookService.processPayment(paymentRequest)
        );

        verify(auditLogRepository, never()).save(any());
        verify(deliveryService, never()).deliver(any());
    }
}
