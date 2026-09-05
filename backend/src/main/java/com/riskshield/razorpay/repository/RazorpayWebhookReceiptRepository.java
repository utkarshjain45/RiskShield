package com.riskshield.razorpay.repository;

import com.riskshield.razorpay.entity.RazorpayWebhookReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RazorpayWebhookReceiptRepository extends JpaRepository<RazorpayWebhookReceipt, String> {

    List<RazorpayWebhookReceipt> findByEntityIdOrderByReceivedAtDesc(String entityId);

    Optional<RazorpayWebhookReceipt> findFirstByEntityIdOrderByReceivedAtDesc(String entityId);

    List<RazorpayWebhookReceipt> findTop50ByOrderByReceivedAtDesc();
}
