package com.example.paymenteventrecoveryengine.repository;

import com.example.paymenteventrecoveryengine.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookRepository extends JpaRepository<PaymentEvent, Long> {

}
