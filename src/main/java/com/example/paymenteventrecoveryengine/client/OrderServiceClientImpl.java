package com.example.paymenteventrecoveryengine.client;

import com.example.paymenteventrecoveryengine.dto.PaymentRequest;
import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OrderServiceClientImpl implements OrderServiceClient {

    private final RestClient restClient;

    public OrderServiceClientImpl(
            @Value("${order-service.url}") String orderServiceUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(orderServiceUrl)
                .build();
    }

    @Override
    public void deliver(PaymentEvent paymentEvent) {

        PaymentRequest request = new PaymentRequest(
                paymentEvent.getPaymentId(),
                paymentEvent.getOrderId(),
                paymentEvent.getAmount(),
                paymentEvent.getCurrency()
        );

        restClient.post()
                .uri("/api/orders/payments")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}