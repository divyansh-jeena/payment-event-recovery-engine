package com.example.paymenteventrecoveryengine.repository;


import com.example.paymenteventrecoveryengine.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog,Long> {
    List<AuditLog> findByPaymentIdOrderByCreatedAtAsc(
            String paymentId
    );

}
