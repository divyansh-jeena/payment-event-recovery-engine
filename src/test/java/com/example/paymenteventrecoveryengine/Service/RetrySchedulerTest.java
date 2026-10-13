package com.example.paymenteventrecoveryengine.Service;

import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrySchedulerTest {

    @InjectMocks
    private RetryScheduler retryScheduler;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private PaymentClaimService paymentClaimService;


    @Test
    void retryPayments_whenNoFailedPaymentsExist() {

        when(paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(PaymentStatus.FAILED),
                any(LocalDateTime.class)
        )).thenReturn(List.of());

        retryScheduler.retryFailedPayments();

        verify(deliveryService, never())
                .deliver(any(PaymentEvent.class));

        verify(paymentClaimService, never())
                .claim(anyLong(), any(PaymentStatus.class));
    }


    @Test
    void retryPayments_whenOneFailedPaymentExists() {

        PaymentEvent payment = mock(PaymentEvent.class);
        when(payment.getId()).thenReturn(1L);

        when(paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(PaymentStatus.FAILED),
                any(LocalDateTime.class)
        )).thenReturn(List.of(payment));

        when(paymentClaimService.claim(
                1L,
                PaymentStatus.FAILED
        )).thenReturn(true);

        retryScheduler.retryFailedPayments();

        verify(paymentClaimService, times(1))
                .claim(1L, PaymentStatus.FAILED);

        verify(deliveryService, times(1))
                .deliver(payment);
    }


    @Test
    void retryPayments_whenManyFailedPaymentsExist() {

        PaymentEvent payment1 = mock(PaymentEvent.class);
        PaymentEvent payment2 = mock(PaymentEvent.class);
        PaymentEvent payment3 = mock(PaymentEvent.class);

        when(payment1.getId()).thenReturn(1L);
        when(payment2.getId()).thenReturn(2L);
        when(payment3.getId()).thenReturn(3L);

        when(paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(PaymentStatus.FAILED),
                any(LocalDateTime.class)
        )).thenReturn(List.of(payment1, payment2, payment3));

        when(paymentClaimService.claim(
                anyLong(),
                eq(PaymentStatus.FAILED)
        )).thenReturn(true);

        retryScheduler.retryFailedPayments();

        verify(paymentClaimService, times(3))
                .claim(anyLong(), eq(PaymentStatus.FAILED));

        verify(deliveryService, times(3))
                .deliver(any(PaymentEvent.class));

        verify(deliveryService).deliver(payment1);
        verify(deliveryService).deliver(payment2);
        verify(deliveryService).deliver(payment3);
    }



    @Test
    void retryPayments_whenClaimFails_shouldNotDeliverPayment() {

        PaymentEvent payment = mock(PaymentEvent.class);
        when(payment.getId()).thenReturn(1L);

        when(paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(PaymentStatus.FAILED),
                any(LocalDateTime.class)
        )).thenReturn(List.of(payment));

        when(paymentClaimService.claim(
                1L,
                PaymentStatus.FAILED
        )).thenReturn(false);

        retryScheduler.retryFailedPayments();

        verify(paymentClaimService, times(1))
                .claim(1L, PaymentStatus.FAILED);

        verify(deliveryService, never())
                .deliver(any(PaymentEvent.class));
    }

    @Test
    void retryPayments_whenSomeClaimsFail_shouldDeliverOnlyClaimedPayments() {

        PaymentEvent payment1 = mock(PaymentEvent.class);
        PaymentEvent payment2 = mock(PaymentEvent.class);
        PaymentEvent payment3 = mock(PaymentEvent.class);

        when(payment1.getId()).thenReturn(1L);
        when(payment2.getId()).thenReturn(2L);
        when(payment3.getId()).thenReturn(3L);



        when(paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(PaymentStatus.FAILED),
                any(LocalDateTime.class)
        )).thenReturn(List.of(payment1, payment2, payment3));

        when(paymentClaimService.claim(1L, PaymentStatus.FAILED))
                .thenReturn(true);
        when(paymentClaimService.claim(2L, PaymentStatus.FAILED))
                .thenReturn(true);
        when(paymentClaimService.claim(3L, PaymentStatus.FAILED))
                .thenReturn(false);

        retryScheduler.retryFailedPayments();

        verify(paymentClaimService).claim(1L, PaymentStatus.FAILED);
        verify(paymentClaimService).claim(2L, PaymentStatus.FAILED);
        verify(paymentClaimService).claim(3L, PaymentStatus.FAILED);

        verify(deliveryService).deliver(payment1);
        verify(deliveryService).deliver(payment2);
        verify(deliveryService, never()).deliver(payment3);

        verify(deliveryService, times(2))
                .deliver(any(PaymentEvent.class));
    }


    @Test
    void retryPayments_whenOnePaymentThrowsException_shouldContinueProcessing() {
        PaymentEvent p1 = mock(PaymentEvent.class);
        PaymentEvent p2 = mock(PaymentEvent.class);

        when(p1.getId()).thenReturn(1L);
        when(p2.getId()).thenReturn(2L);

        when(paymentRepository.findByStatusAndNextRetryAtLessThanEqual(
                eq(PaymentStatus.FAILED),
                any(LocalDateTime.class)))
                .thenReturn(List.of(p1, p2));

        when(paymentClaimService.claim(
                anyLong(), eq(PaymentStatus.FAILED)))
                .thenReturn(true);

        doThrow(new RuntimeException("Test exception"))
                .when(deliveryService).deliver(p1);

        retryScheduler.retryFailedPayments();

        verify(deliveryService).deliver(p1);
        verify(deliveryService).deliver(p2);
    }
}
