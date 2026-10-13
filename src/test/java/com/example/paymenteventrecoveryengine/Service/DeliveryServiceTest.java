
        package com.example.paymenteventrecoveryengine.Service;

import com.example.paymenteventrecoveryengine.Configuration.RetryProperties;
import com.example.paymenteventrecoveryengine.client.OrderServiceClient;
import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.AuditAction;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private RetryProperties retryProperties;

    @Mock
    private AuditService auditService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    @InjectMocks
    private DeliveryService deliveryService;

    private PaymentEvent paymentEvent;

    @BeforeEach
    void setUp() {
        paymentEvent = new PaymentEvent();
        paymentEvent.setPaymentId("PAY-TEST-001");
        paymentEvent.setStatus(PaymentStatus.PENDING);
        paymentEvent.setRetryCount(0);
    }

    @Test
    void deliver_whenSuccessful_marksPaymentDelivered() {

        deliveryService.deliver(paymentEvent);

        assertEquals(PaymentStatus.DELIVERED,
                paymentEvent.getStatus());
        assertEquals(0, paymentEvent.getRetryCount());
        assertNull(paymentEvent.getFailureReason());
        assertNull(paymentEvent.getNextRetryAt());

        verify(orderServiceClient).deliver(paymentEvent);
        verify(paymentRepository).save(paymentEvent);

        verify(auditService).createAuditLog(
                "PAY-TEST-001",
                AuditAction.DELIVERY_ATTEMPTED.name(),
                "Attempting delivery to Order Service."
        );

        verify(auditService).createAuditLog(
                "PAY-TEST-001",
                AuditAction.DELIVERY_SUCCESSFUL.name(),
                "Payment delivered successfully."
        );
    }

    // Test 2: Downstream failure schedules a retry
    @Test
    void deliver_whenDownstreamFails_marksPaymentFailedAndSchedulesRetry() {

        when(retryProperties.getMaxAttempts()).thenReturn(5);
        when(retryProperties.getInitialDelay())
                .thenReturn(Duration.ofSeconds(10));
        when(retryProperties.getMultiplier()).thenReturn(2.0);
        when(retryProperties.getMaxDelay())
                .thenReturn(Duration.ofMinutes(10));

        doThrow(new RuntimeException("HTTP 500"))
                .when(orderServiceClient)
                .deliver(paymentEvent);

        LocalDateTime before = LocalDateTime.now();

        deliveryService.deliver(paymentEvent);

        LocalDateTime after = LocalDateTime.now();

        assertEquals(PaymentStatus.FAILED,
                paymentEvent.getStatus());
        assertEquals(1, paymentEvent.getRetryCount());
        assertEquals("HTTP 500",
                paymentEvent.getFailureReason());

        assertNotNull(paymentEvent.getNextRetryAt());

        assertFalse(paymentEvent.getNextRetryAt()
                .isBefore(before.plusSeconds(9)));

        assertFalse(paymentEvent.getNextRetryAt()
                .isAfter(after.plusSeconds(11)));

        verify(paymentRepository).save(paymentEvent);

        verify(auditService).createAuditLog(
                "PAY-TEST-001",
                AuditAction.DELIVERY_FAILED.name(),
                "HTTP 500"
        );
    }

    // Test 3: Maximum attempts reached moves payment to DEAD
    @Test
    void deliver_whenRetryLimitReached_marksPaymentDead() {

        when(retryProperties.getMaxAttempts()).thenReturn(3);

        paymentEvent.setRetryCount(2);

        doThrow(new RuntimeException("HTTP 503"))
                .when(orderServiceClient)
                .deliver(paymentEvent);

        deliveryService.deliver(paymentEvent);

        assertEquals(PaymentStatus.DEAD,
                paymentEvent.getStatus());
        assertEquals(3, paymentEvent.getRetryCount());
        assertEquals("HTTP 503",
                paymentEvent.getFailureReason());
        assertNull(paymentEvent.getNextRetryAt());

        verify(paymentRepository).save(paymentEvent);

        verify(auditService).createAuditLog(
                "PAY-TEST-001",
                AuditAction.MOVED_TO_DLQ.name(),
                "Maximum retry limit reached."
        );

        verify(auditService, never()).createAuditLog(
                "PAY-TEST-001",
                AuditAction.DELIVERY_FAILED.name(),
                "HTTP 503"
        );
    }

    // Test 4: Exponential backoff increases the retry delay
    @Test
    void deliver_whenSecondAttemptFails_usesExponentialBackoff() {

        when(retryProperties.getMaxAttempts()).thenReturn(5);
        when(retryProperties.getInitialDelay())
                .thenReturn(Duration.ofSeconds(10));
        when(retryProperties.getMultiplier()).thenReturn(2.0);
        when(retryProperties.getMaxDelay())
                .thenReturn(Duration.ofMinutes(10));

        paymentEvent.setRetryCount(1);

        doThrow(new RuntimeException("Connection timeout"))
                .when(orderServiceClient)
                .deliver(paymentEvent);

        LocalDateTime before = LocalDateTime.now();

        deliveryService.deliver(paymentEvent);

        LocalDateTime after = LocalDateTime.now();

        assertEquals(PaymentStatus.FAILED,
                paymentEvent.getStatus());
        assertEquals(2, paymentEvent.getRetryCount());

        // Second failure: 10 * 2^(2 - 1) = 20 seconds.
        assertNotNull(paymentEvent.getNextRetryAt());

        assertFalse(paymentEvent.getNextRetryAt()
                .isBefore(before.plusSeconds(19)));

        assertFalse(paymentEvent.getNextRetryAt()
                .isAfter(after.plusSeconds(21)));
    }

    // Test 5: Retry delay is capped at the maximum
    @Test
    void deliver_whenBackoffExceedsMaximum_capsRetryDelay() {

        when(retryProperties.getMaxAttempts()).thenReturn(10);
        when(retryProperties.getInitialDelay())
                .thenReturn(Duration.ofMinutes(5));
        when(retryProperties.getMultiplier()).thenReturn(2.0);
        when(retryProperties.getMaxDelay())
                .thenReturn(Duration.ofMinutes(10));

        paymentEvent.setRetryCount(2);

        doThrow(new RuntimeException("HTTP 500"))
                .when(orderServiceClient)
                .deliver(paymentEvent);

        LocalDateTime before = LocalDateTime.now();

        deliveryService.deliver(paymentEvent);

        LocalDateTime after = LocalDateTime.now();

        assertEquals(PaymentStatus.FAILED,
                paymentEvent.getStatus());

        // Calculated delay is 20 minutes, capped at 10 minutes.
        assertNotNull(paymentEvent.getNextRetryAt());

        assertFalse(paymentEvent.getNextRetryAt()
                .isBefore(before.plusMinutes(9)));

        assertFalse(paymentEvent.getNextRetryAt()
                .isAfter(after.plusMinutes(11)));
    }
}
