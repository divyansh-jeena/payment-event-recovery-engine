package com.example.paymenteventrecoveryengine.Service;


import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.time.LocalDateTime;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentClaimServiceTest {

    @InjectMocks
    private PaymentClaimService paymentClaimService;

  @Mock
  private PaymentRepository paymentRepository;
    @Test
    void claim_returnsTrue_whenPaymentIsClaimed() {
        when(paymentRepository.claimPayment(
                anyLong(),
                eq(PaymentStatus.PROCESSING),
                eq(PaymentStatus.FAILED),
                any(LocalDateTime.class))
        ).thenReturn(1);

        boolean isExecuted =
                paymentClaimService.claim(1L, PaymentStatus.FAILED);

        assertTrue(isExecuted);
    }
    @Test
    void claim_returnsFalse_whenNoPaymentCanBeClaimed() {
        when(paymentRepository.claimPayment(
                anyLong(),
                eq(PaymentStatus.PROCESSING),
                eq(PaymentStatus.FAILED)
                ,any(LocalDateTime.class)
        )).thenReturn(0);

        boolean isExecuted =
                paymentClaimService.claim(1L, PaymentStatus.FAILED);

        assertFalse(isExecuted);
    }


}
