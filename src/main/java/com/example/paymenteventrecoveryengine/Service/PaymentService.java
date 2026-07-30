package com.example.paymenteventrecoveryengine.Service;


import com.example.paymenteventrecoveryengine.dto.AuditLogResponse;
import com.example.paymenteventrecoveryengine.dto.PaymentResponse;

import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import com.example.paymenteventrecoveryengine.enums.AuditAction;
import com.example.paymenteventrecoveryengine.dto.MetricsResponse;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import com.example.paymenteventrecoveryengine.exception.PaymentNotFoundException;
import com.example.paymenteventrecoveryengine.repository.AuditLogRepository;
import com.example.paymenteventrecoveryengine.repository.PaymentRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class PaymentService {
     private final PaymentRepository paymentRepository;
     private  final AuditLogRepository auditLogRepository;
     private final DeliveryService deliveryService;
     private final AuditService auditService;


    public PaymentService(PaymentRepository paymentRepository, AuditLogRepository auditLogRepository, DeliveryService deliveryService, AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.auditLogRepository = auditLogRepository;
        this.deliveryService = deliveryService;
        this.auditService = auditService;
    }

    private PaymentEvent findPaymentOrThrow(String paymentId) {
        return paymentRepository
                .findByPaymentId(paymentId)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "Payment not found with id: " + paymentId
                        )
                );
    }

    public PaymentResponse getPayment(String paymentId) {
        log.info("Fetching payment {}", paymentId);

        return  toResponse(findPaymentOrThrow(paymentId));
    }

    public Page<PaymentResponse> getPayments( int page,  int size) {
        log.info(
                "Fetching payments. page={}, size={}",
                page,
                size

        );

        Pageable pageable = PageRequest.of(page, size);
        return paymentRepository.findAll(pageable)
                .map(this::toResponse);



    }

    private PaymentResponse toResponse(PaymentEvent paymentEvent) {
        return new
                PaymentResponse(paymentEvent.getPaymentId(),
                paymentEvent.getOrderId(),
                paymentEvent.getAmount(),
                paymentEvent.getCurrency(),
                paymentEvent.getStatus().toString(),
                paymentEvent.getRetryCount(),
                paymentEvent.getFailureReason());
    }

    public Page<PaymentResponse> getPaymentsByStatus(PaymentStatus status, int page, int size) {
        log.info(
                "Fetching payments with status {}. page={}, size={}",
                status,
                page,
                size
        );
       Pageable pageable=PageRequest.of(page,size);
       return paymentRepository.findByStatus(status,pageable).map(this::toResponse);


    }

    public List<AuditLogResponse> getPaymentLogs(
            String paymentId
    ) {
        findPaymentOrThrow( paymentId);
        return auditLogRepository
                .findByPaymentIdOrderByCreatedAtAsc(paymentId)
                .stream()
                .map(log -> new AuditLogResponse(
                        log.getAction(),
                        log.getMessage(),
                        log.getCreatedAt()
                ))
                .toList();

    }

    public void manualRetry(String paymentId) {

        log.info("Manual retry requested for payment {}", paymentId);
         PaymentEvent paymentEvent= findPaymentOrThrow(paymentId);
          if(paymentEvent.getStatus()==PaymentStatus.DEAD ||paymentEvent.getStatus()== PaymentStatus.FAILED){

             auditService.createAuditLog(
                      paymentId,
                     AuditAction.MANUAL_RETRY_STARTED.name(),
                      "Admin initiated manual retry."
              );
               if(paymentEvent.getStatus()==PaymentStatus.DEAD){
                   paymentEvent.setStatus(PaymentStatus.FAILED);
               }
              deliveryService.deliver(paymentEvent);
              log.info("Manual retry initiated for payment {}", paymentId);

          }
          else{
              log.warn(
                      "Manual retry rejected for payment {} because status is {}",
                      paymentId,
                      paymentEvent.getStatus()
              );
              throw  new IllegalArgumentException("Manual retry is only allowed for FAILED or DEAD payments.");

          }


    }

    public Page<PaymentResponse> getDeadPayments(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        return paymentRepository
                .findByStatus(PaymentStatus.DEAD, pageable)
                .map(this::toResponse);
    }
    public MetricsResponse getMetrics() {
        log.info("Fetching payment metrics.");

        return new MetricsResponse(
                paymentRepository.count(),
                paymentRepository.countByStatus(PaymentStatus.PENDING),
                paymentRepository.countByStatus(PaymentStatus.FAILED),
                paymentRepository.countByStatus(PaymentStatus.DELIVERED),
                paymentRepository.countByStatus(PaymentStatus.DEAD)
        );
    }
}
