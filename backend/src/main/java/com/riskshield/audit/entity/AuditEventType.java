package com.riskshield.audit.entity;

/**
 * Enumeration of all auditable risk, policy, machine learning, incident, and investigation lifecycle events.
 */
public enum AuditEventType {
    TRANSACTION_RECEIVED,
    FEATURES_GENERATED,
    MODEL_SCORED,
    POLICY_EVALUATED,
    RISK_DECISION_CREATED,
    ALERT_CREATED,
    INCIDENT_CREATED,
    INCIDENT_ACKNOWLEDGED,
    INCIDENT_RESOLVED,
    AI_INVESTIGATION_STARTED,
    AI_TOOL_CALLED,
    AI_RESPONSE_GENERATED,
    WEBHOOK_RECEIVED,
    WEBHOOK_REJECTED
}
