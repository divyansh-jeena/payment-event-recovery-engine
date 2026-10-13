package com.example.paymenteventrecoveryengine.repository;

import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository
        extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByPaymentId(
            String paymentId
    );

    boolean existsByPaymentId(
            String paymentId
    );

    Page<PaymentEvent> findByStatus(
            PaymentStatus status,
            Pageable pageable
    );

    List<PaymentEvent> findByStatusAndNextRetryAtBefore(
            PaymentStatus status,
            LocalDateTime time
    );

    List<PaymentEvent> findByStatusAndNextRetryAtLessThanEqual(
            PaymentStatus status,
            LocalDateTime nextRetryAt
    );
    long countByStatus(PaymentStatus status);

    @Modifying
    @Query("""
    UPDATE PaymentEvent p
    SET p.status = :processing
    WHERE p.id = :id
      AND p.status = :expectedStatus
    """)
    int claimPayment(
            @Param("id") Long id,
            @Param("processing") PaymentStatus processing,
            @Param("expectedStatus") PaymentStatus expectedStatus
    );

}