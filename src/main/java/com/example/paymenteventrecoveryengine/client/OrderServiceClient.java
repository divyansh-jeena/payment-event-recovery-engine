package com.example.paymenteventrecoveryengine.client;



import com.example.paymenteventrecoveryengine.entity.PaymentEvent;

public interface OrderServiceClient {

    void deliver(PaymentEvent paymentEvent);
}