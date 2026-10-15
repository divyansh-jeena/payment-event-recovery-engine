package com.example.paymenteventrecoveryengine.entity;

import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_events_payment_id",
                columnNames = "payment_id"
        ),
        indexes = @Index(
                name = "idx_payment_events_status_next_retry_at",
                columnList = "status, next_retry_at"
        )
)
@Getter
@Setter

public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;
    @Column(name = "payment_id", nullable = false, length = 255)
    private String paymentId;
    @Column(name = "order_id", nullable = false, length = 255)
    private String orderId;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;




}
