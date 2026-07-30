package com.example.paymenteventrecoveryengine.Service;


import com.example.paymenteventrecoveryengine.entity.AuditLog;
import com.example.paymenteventrecoveryengine.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public   void createAuditLog(String paymentId,
                                 String action,
                                 String message) {

        AuditLog auditLog=new AuditLog();
        auditLog.setPaymentId(paymentId);
        auditLog.setAction(action);
        auditLog.setMessage(message);
        auditLogRepository.save(auditLog);
        log.info(
                "Audit log created. paymentId={}, action={}",
                paymentId,
                action
        );

    }
}
