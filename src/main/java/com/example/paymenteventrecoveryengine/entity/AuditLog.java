package com.example.paymenteventrecoveryengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "audit_logs",
        indexes = @Index(
                name = "idx_audit_logs_payment_id_created_at",
                columnList = "payment_id, created_at"
        )
)
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", length = 255)
    private String paymentId;

    @Column(name = "action", length = 255)
    private String action;

    @Column(name = "message", length = 255)
    private String message;

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;
}
