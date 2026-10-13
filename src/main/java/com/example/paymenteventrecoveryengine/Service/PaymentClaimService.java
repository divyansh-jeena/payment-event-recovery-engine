

package com.example.paymenteventrecoveryengine.Service;

import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentClaimService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public boolean claim(Long paymentId, PaymentStatus expectedStatus) {
        int updated = paymentRepository.claimPayment(
                paymentId,
                PaymentStatus.PROCESSING,
                expectedStatus
        );

        return updated == 1;
    }
}
