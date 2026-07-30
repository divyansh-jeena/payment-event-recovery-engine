package com.example.paymenteventrecoveryengine.entity;

import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_events")
@Getter
@Setter

public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
   private   Long id;
    @Column(nullable = false, unique = true )
    private String  paymentId;
    @Column(nullable = false)
    private  String  orderId;
    @Column(nullable = false )
    private  BigDecimal amount;
    @Column(length = 3,nullable = false)
    private   String  currency;
    private Integer retryCount=0;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;
    private String failureReason;

    private LocalDateTime nextRetryAt;
    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;




}
