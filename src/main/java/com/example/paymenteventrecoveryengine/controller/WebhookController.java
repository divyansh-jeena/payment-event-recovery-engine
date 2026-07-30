package com.example.paymenteventrecoveryengine.controller;

import com.example.paymenteventrecoveryengine.Service.WebhookService;
import com.example.paymenteventrecoveryengine.dto.PaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/payments")
    public ResponseEntity<String> payment(
            @Valid @RequestBody PaymentRequest paymentRequest
    ) {

        webhookService.processPayment(paymentRequest);

        return ResponseEntity.ok(
                "Payment accepted successfully"
        );
    }
}