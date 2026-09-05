package com.riskshield.audit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for generating cryptographic hashes of audit payloads to provide non-repudiation and tamper detection.
 */
@Slf4j
public final class AuditCryptoUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AuditCryptoUtils() {}

    /**
     * Computes the SHA-256 hex digest of the given object or string.
     */
    public static String computeSha256(Object payload) {
        if (payload == null) {
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // SHA-256 of empty string
        }

        try {
            String json;
            if (payload instanceof String str) {
                json = str;
            } else {
                json = OBJECT_MAPPER.writeValueAsString(payload);
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm unavailable", e);
            throw new IllegalStateException("Cryptographic algorithm SHA-256 unavailable", e);
        } catch (Exception e) {
            log.warn("Failed to serialize payload for audit hash calculation: {}", e.getMessage());
            return "hash_calc_error_" + System.currentTimeMillis();
        }
    }
}
