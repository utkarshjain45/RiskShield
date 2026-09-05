package com.riskshield.security.model;

/**
 * Standard authorized roles within RiskShield AI platform.
 */
public enum UserRole {
    ADMIN,
    RISK_ANALYST,
    MERCHANT_VIEWER;

    public String authority() {
        return "ROLE_" + this.name();
    }
}
