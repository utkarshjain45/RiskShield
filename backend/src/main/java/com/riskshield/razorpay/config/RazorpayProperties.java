package com.riskshield.razorpay.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class RazorpayProperties {

    @Value("${riskshield.razorpay.key-id:rzp_test_placeholder}")
    private String keyId;

    @Value("${riskshield.razorpay.key-secret:rzp_test_secret_placeholder}")
    private String keySecret;

    @Value("${riskshield.razorpay.webhook-secret:rzp_test_webhook_secret}")
    private String webhookSecret;

    public boolean isTestMode() {
        return keyId != null && keyId.startsWith("rzp_test_");
    }

    /**
     * Never log or leak secrets in toString().
     */
    @Override
    public String toString() {
        return "RazorpayProperties[keyId=" + (keyId != null && keyId.length() > 8 ? keyId.substring(0, 8) + "..." : "unset") +
                ", isTestMode=" + isTestMode() + ", webhookSecretConfigured=" + (webhookSecret != null && !webhookSecret.isBlank()) + "]";
    }
}
