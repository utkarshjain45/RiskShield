package com.riskshield.audit.entity;

/**
 * Classification of actors initiating an auditable action.
 */
public enum ActorType {
    SYSTEM,
    USER,
    MERCHANT,
    ML_SERVICE,
    LLM
}
