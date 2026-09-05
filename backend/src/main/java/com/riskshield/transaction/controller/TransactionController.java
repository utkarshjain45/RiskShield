package com.riskshield.transaction.controller;

import com.riskshield.common.dto.ApiResponse;
import com.riskshield.transaction.dto.CreateTransactionRequest;
import com.riskshield.transaction.dto.TransactionResponse;
import com.riskshield.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Payment transaction ingestion and lifecycle management")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Ingest a new payment transaction")
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        TransactionResponse response = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Transaction ingested successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve payment transaction details by ID")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(@PathVariable("id") String id) {
        TransactionResponse response = transactionService.getTransaction(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "Query paginated list of transactions with optional search and filters")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String decision,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<TransactionResponse> response = transactionService.getTransactions(search, paymentStatus, merchantId, decision, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/investigation")
    @Operation(summary = "Retrieve comprehensive 360-degree investigation dossier for a transaction")
    public ResponseEntity<ApiResponse<com.riskshield.transaction.dto.TransactionInvestigationDto>> getInvestigation(
            @PathVariable("id") String id
    ) {
        com.riskshield.transaction.dto.TransactionInvestigationDto dossier = transactionService.getTransactionInvestigation(id);
        return ResponseEntity.ok(ApiResponse.ok(dossier));
    }
}
