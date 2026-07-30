package com.example.paymenteventrecoveryengine.controller;


import com.example.paymenteventrecoveryengine.Service.PaymentService;
import com.example.paymenteventrecoveryengine.dto.AuditLogResponse;
import com.example.paymenteventrecoveryengine.dto.PaymentResponse;
import com.example.paymenteventrecoveryengine.dto.MetricsResponse;
import com.example.paymenteventrecoveryengine.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable String paymentId
    ) {
        return ResponseEntity.ok(
                paymentService.getPayment(paymentId)
        );
    }



    @GetMapping
    public ResponseEntity<Page<PaymentResponse>> getPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (status == null) {
            return ResponseEntity.ok(paymentService.getPayments(page, size));
        }

        return ResponseEntity.ok(
                paymentService.getPaymentsByStatus(status, page, size)
        );
    }
    @GetMapping("/{paymentId}/logs")
    public ResponseEntity<List<AuditLogResponse>> getPaymentLogs(
            @PathVariable String paymentId
    ) {

        return ResponseEntity.ok(
                paymentService.getPaymentLogs(paymentId)
        );
    }

    @PostMapping("/{paymentId}/retry")
    public ResponseEntity<String> retryPayment(
            @PathVariable String paymentId){

        paymentService.manualRetry(paymentId);

        return ResponseEntity.ok("Manual retry initiated.");
    }
    @GetMapping("/dead")
    public ResponseEntity<Page<PaymentResponse>> getDeadPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(
                paymentService.getDeadPayments(page, size)
        );
    }

    @GetMapping("/metrics")
    public ResponseEntity<MetricsResponse> getMetrics() {

        return ResponseEntity.ok(
                paymentService.getMetrics()
        );
    }

}