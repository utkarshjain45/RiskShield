package com.riskshield.razorpay.service;

import com.riskshield.razorpay.config.RazorpayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Validates incoming Razorpay Webhook signatures using HMAC-SHA256
 * per official Razorpay API specifications.
 * All comparisons are constant-time to eliminate timing attack vectors.
 * Secrets are strictly safeguarded and never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpaySignatureValidator {

    private final RazorpayProperties razorpayProperties;
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    /**
     * Validates that the provided webhook signature matches the HMAC-SHA256 hash of the raw request payload.
     *
     * @param rawBody The unmodified, raw HTTP request body
     * @param signature The signature provided in the X-Razorpay-Signature header
     * @return true if the signature is valid, false otherwise
     */
    public boolean isValid(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook rejection: Missing or empty X-Razorpay-Signature header");
            return false;
        }

        if (rawBody == null) {
            log.warn("Webhook rejection: Null raw request payload");
            return false;
        }

        String webhookSecret = razorpayProperties.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Webhook rejection: RAZORPAY_WEBHOOK_SECRET is not configured on this server");
            return false;
        }

        try {
            String calculatedSignature = computeHmacSha256(rawBody, webhookSecret);
            boolean match = MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );

            if (!match) {
                log.warn("Webhook rejection: Signature mismatch for incoming payload (length: {} bytes)", rawBody.length());
            }

            return match;
        } catch (Exception e) {
            log.error("Webhook signature validation exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Computes the HMAC-SHA256 hex digest for a given payload and secret.
     * Visible for testing and local replay payload generation.
     */
    public String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
