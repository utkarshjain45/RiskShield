package com.riskshield.transaction.service;

import com.riskshield.audit.service.AuditService;
import com.riskshield.common.exception.ResourceNotFoundException;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.merchant.repository.MerchantRepository;
import com.riskshield.transaction.dto.CreateTransactionRequest;
import com.riskshield.transaction.dto.TransactionResponse;
import com.riskshield.transaction.entity.Customer;
import com.riskshield.transaction.entity.Device;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.CustomerRepository;
import com.riskshield.transaction.repository.DeviceRepository;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.policy.repository.RiskDecisionRepository;
import com.riskshield.risk.dto.RiskExplanationResponse;
import com.riskshield.risk.entity.RiskAssessment;
import com.riskshield.risk.repository.RiskAssessmentRepository;
import com.riskshield.risk.service.RiskAssessmentService;
import com.riskshield.transaction.dto.TransactionInvestigationDto;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final CustomerRepository customerRepository;
    private final DeviceRepository deviceRepository;
    private final AuditService auditService;
    private final com.riskshield.event.producer.PaymentEventProducer paymentEventProducer;
    private final RiskDecisionRepository riskDecisionRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskAssessmentService riskAssessmentService;

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest req) {
        // Enforce tenant isolation for callers with assigned merchant scope
        com.riskshield.security.util.SecurityUtils.assertMerchantAccess(req.getMerchantId());

        log.info("Creating new payment transaction for merchant: {}, amount: {} paise",
                req.getMerchantId(), req.getAmountInPaise());

        // 1. Resolve or create Merchant
        Merchant merchant = merchantRepository.findById(req.getMerchantId())
                .orElseGet(() -> merchantRepository.save(
                        Merchant.builder()
                                .id(req.getMerchantId())
                                .name("Merchant " + req.getMerchantId())
                                .email("contact@" + req.getMerchantId() + ".com")
                                .category("grocery_supermarket")
                                .riskTier("MEDIUM")
                                .active(true)
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build()
                ));

        // 2. Resolve or create Customer
        Customer customer = null;
        if (req.getCustomerId() != null) {
            customer = customerRepository.findById(req.getCustomerId())
                    .orElseGet(() -> customerRepository.save(
                            Customer.builder()
                                    .id(req.getCustomerId())
                                    .email(req.getCustomerEmail())
                                    .contact(req.getCustomerContact())
                                    .billingState(req.getCustomerBillingState())
                                    .riskSegment("standard")
                                    .accountCreatedAt(Instant.now())
                                    .createdAt(Instant.now())
                                    .build()
                    ));
        }

        // 3. Resolve or create Device
        Device device = null;
        if (req.getDeviceId() != null) {
            device = deviceRepository.findById(req.getDeviceId())
                    .orElseGet(() -> deviceRepository.save(
                            Device.builder()
                                    .id(req.getDeviceId())
                                    .deviceType(req.getDeviceType() != null ? req.getDeviceType() : "mobile_android")
                                    .isEmulator(req.getIsEmulator() != null ? req.getIsEmulator() : false)
                                    .firstSeenAt(Instant.now())
                                    .build()
                    ));
        }

        // 4. Build Transaction
        String txId = req.getTransactionId();
        if (txId == null || txId.isBlank()) {
            txId = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        Transaction transaction = Transaction.builder()
                .id(txId)
                .merchant(merchant)
                .customer(customer)
                .device(device)
                .ipAddress(req.getIpAddress())
                .amountInPaise(req.getAmountInPaise())
                .currency(req.getCurrency() != null ? req.getCurrency() : "INR")
                .paymentMethod(req.getPaymentMethod())
                .paymentStatus("PENDING")
                .customerAccountAgeDays(req.getCustomerAccountAgeDays() != null ? req.getCustomerAccountAgeDays() : 30)
                .isNewDevice(req.getIsNewDevice() != null ? req.getIsNewDevice() : false)
                .isNewIp(req.getIsNewIp() != null ? req.getIsNewIp() : false)
                .createdAt(Instant.now())
                .build();

        Transaction savedTx = transactionRepository.save(transaction);

        // 5. Record Audit Event: TRANSACTION_RECEIVED
        auditService.recordRiskEvent(
                com.riskshield.audit.entity.AuditEventType.TRANSACTION_RECEIVED,
                com.riskshield.audit.entity.ActorType.MERCHANT,
                savedTx.getMerchant().getId(),
                savedTx.getMerchant().getId(),
                savedTx,
                "Transaction",
                savedTx.getId(),
                "transaction-service",
                String.format("Payment transaction received for amount ₹%.2f via %s",
                        savedTx.getAmountInPaise() / 100.0, savedTx.getPaymentMethod()),
                req
        );

        // 6. Publish to Kafka event stream
        String correlationId = org.slf4j.MDC.get("correlationId");
        paymentEventProducer.publishPaymentCreated(savedTx, correlationId);

        return mapToResponse(savedTx);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String id) {
        Transaction tx = transactionRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        if (tx.getMerchant() != null) {
            com.riskshield.security.util.SecurityUtils.assertMerchantAccess(tx.getMerchant().getId());
        }
        return mapToResponse(tx);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(Pageable pageable) {
        String effectiveMerchant = com.riskshield.security.util.SecurityUtils.resolveMerchantScope(null);
        if (effectiveMerchant != null) {
            return transactionRepository.findByMerchantIdOrderByCreatedAtDesc(effectiveMerchant, pageable).map(this::mapToResponse);
        }
        return transactionRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(String search, String paymentStatus, String merchantId, String decision, Pageable pageable) {
        String effectiveMerchant = com.riskshield.security.util.SecurityUtils.resolveMerchantScope(merchantId);

        Page<Transaction> page;
        if ((search != null && !search.isBlank()) || (paymentStatus != null && !paymentStatus.isBlank()) || (effectiveMerchant != null && !effectiveMerchant.isBlank())) {
            page = transactionRepository.searchTransactions(search, paymentStatus, effectiveMerchant, pageable);
        } else {
            page = transactionRepository.findAll(pageable);
        }

        Page<TransactionResponse> mapped = page.map(this::mapToResponse);
        return mapped;
    }

    @Transactional(readOnly = true)
    public TransactionInvestigationDto getTransactionInvestigation(String id) {
        Transaction tx = transactionRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));

        if (tx.getMerchant() != null) {
            com.riskshield.security.util.SecurityUtils.assertMerchantAccess(tx.getMerchant().getId());
        }

        TransactionResponse txResponse = mapToResponse(tx);

        RiskExplanationResponse explanation = null;
        try {
            explanation = riskAssessmentService.getTransactionExplanation(id);
        } catch (Exception e) {
            log.warn("Could not retrieve explanation for tx {}: {}", id, e.getMessage());
        }

        // Customer Summary
        Customer c = tx.getCustomer();
        long totalCustTx = c != null ? transactionRepository.countByCustomer(c) : 1;
        Double avgCustPaise = c != null ? transactionRepository.calculateAvgAmountInPaiseByCustomer(c) : (double) tx.getAmountInPaise();
        TransactionInvestigationDto.CustomerSummaryDto custSummary = TransactionInvestigationDto.CustomerSummaryDto.builder()
                .customerId(c != null ? c.getId() : "N/A")
                .email(c != null ? c.getEmail() : "N/A")
                .accountAgeDays(tx.getCustomerAccountAgeDays() != null ? tx.getCustomerAccountAgeDays() : 30)
                .totalTransactions(totalCustTx)
                .averageAmountInr(avgCustPaise != null ? avgCustPaise / 100.0 : (tx.getAmountInPaise() != null ? tx.getAmountInPaise() / 100.0 : 0.0))
                .riskSegment(c != null && c.getRiskSegment() != null ? c.getRiskSegment() : "standard")
                .build();

        // Device Summary
        Device d = tx.getDevice();
        long totalDevTx = d != null ? transactionRepository.countByDevice(d) : 1;
        long distinctDevCust = d != null ? transactionRepository.countDistinctCustomersByDevice(d) : 1;
        TransactionInvestigationDto.DeviceSummaryDto devSummary = TransactionInvestigationDto.DeviceSummaryDto.builder()
                .deviceId(d != null ? d.getId() : "N/A")
                .totalTransactions(totalDevTx)
                .distinctAccounts(distinctDevCust)
                .isNewDevice(tx.isNewDevice())
                .build();

        // IP Summary
        String ip = tx.getIpAddress() != null ? tx.getIpAddress() : "127.0.0.1";
        long totalIpTx = transactionRepository.countByIpAddress(ip);
        long distinctIpCust = transactionRepository.countDistinctCustomersByIpAddress(ip);
        TransactionInvestigationDto.IpSummaryDto ipSummary = TransactionInvestigationDto.IpSummaryDto.builder()
                .ipAddress(ip)
                .totalTransactions(totalIpTx)
                .distinctAccounts(distinctIpCust)
                .isNewIp(tx.isNewIp())
                .build();

        // Related Transactions (e.g. recent from this customer)
        List<TransactionResponse> relatedTxs = Collections.emptyList();
        if (c != null) {
            relatedTxs = transactionRepository.findByCustomerOrderByCreatedAtDesc(c, PageRequest.of(0, 5))
                    .stream()
                    .filter(t -> !t.getId().equals(id))
                    .map(this::mapToResponse)
                    .toList();
        }

        // Audit Trail
        List<com.riskshield.audit.dto.AuditEventResponse> auditTrail = auditService.getEventsForTransaction(id);

        return TransactionInvestigationDto.builder()
                .transaction(txResponse)
                .explanation(explanation)
                .customerSummary(custSummary)
                .deviceSummary(devSummary)
                .ipSummary(ipSummary)
                .relatedTransactions(relatedTxs)
                .auditTrail(auditTrail)
                .build();
    }

    private TransactionResponse mapToResponse(Transaction tx) {
        Double riskScore = null;
        String decisionStr = null;

        Optional<RiskDecision> decOpt = riskDecisionRepository.findByTransactionId(tx.getId());
        if (decOpt.isPresent()) {
            decisionStr = decOpt.get().getDecision().name();
            riskScore = decOpt.get().getRiskScore();
        } else {
            Optional<RiskAssessment> astOpt = riskAssessmentRepository.findByTransactionId(tx.getId());
            if (astOpt.isPresent()) {
                riskScore = astOpt.get().getRiskScore();
                decisionStr = riskScore >= 90.0 ? "BLOCK" : (riskScore >= 31.0 ? "REVIEW" : "ALLOW");
            }
        }

        return TransactionResponse.builder()
                .id(tx.getId())
                .merchantId(tx.getMerchant() != null ? tx.getMerchant().getId() : null)
                .merchantName(tx.getMerchant() != null ? tx.getMerchant().getName() : null)
                .customerId(tx.getCustomer() != null ? tx.getCustomer().getId() : null)
                .deviceId(tx.getDevice() != null ? tx.getDevice().getId() : null)
                .ipAddress(tx.getIpAddress())
                .amountInPaise(tx.getAmountInPaise())
                .amountInInr(tx.getAmountInPaise() != null ? tx.getAmountInPaise() / 100.0 : 0.0)
                .currency(tx.getCurrency())
                .paymentMethod(tx.getPaymentMethod())
                .paymentStatus(tx.getPaymentStatus())
                .customerAccountAgeDays(tx.getCustomerAccountAgeDays())
                .isNewDevice(tx.isNewDevice())
                .isNewIp(tx.isNewIp())
                .riskScore(riskScore)
                .decision(decisionStr)
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
