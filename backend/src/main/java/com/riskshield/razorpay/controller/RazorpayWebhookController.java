package com.riskshield.razorpay.controller;

import com.riskshield.razorpay.dto.RazorpayReplayRequest;
import com.riskshield.razorpay.dto.RazorpayWebhookResponse;
import com.riskshield.razorpay.service.RazorpayWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
@RequiredArgsConstructor
@Tag(name = "Razorpay Webhooks", description = "Secure webhook ingestion and local replay for Razorpay Test Mode")
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    /**
     * Official Razorpay Webhook receiver endpoint.
     * Validates HMAC-SHA256 signature using the exact raw request payload.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Receive and process official Razorpay webhook events asynchronously")
    public ResponseEntity<?> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventIdHeader,
            @RequestBody String rawPayload
    ) {
        try {
            RazorpayWebhookResponse response = webhookService.processWebhook(rawPayload, signature, eventIdHeader);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            log.warn("Unauthorized webhook request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(RazorpayWebhookResponse.builder()
                    .status("UNAUTHORIZED")
                    .message("Invalid webhook signature")
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("Malformed webhook payload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(RazorpayWebhookResponse.builder()
                    .status("BAD_REQUEST")
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Internal error processing Razorpay webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(RazorpayWebhookResponse.builder()
                    .status("ERROR")
                    .message("Internal webhook processing error")
                    .build());
        }
    }

    /**
     * Local development replay mechanism:
     * Generates a realistic, validly signed Razorpay test webhook payload and executes
     * the ingestion, idempotency, and Kafka risk assessment pipeline.
     */
    @PostMapping(value = "/replay", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Local development mechanism to replay sanitized Razorpay webhook payloads")
    public ResponseEntity<RazorpayWebhookResponse> replayWebhook(
            @RequestBody(required = false) RazorpayReplayRequest request
    ) {
        RazorpayReplayRequest req = request != null ? request : new RazorpayReplayRequest();
        RazorpayWebhookResponse response = webhookService.replayWebhook(req);
        return ResponseEntity.ok(response);
    }
}
