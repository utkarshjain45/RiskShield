-- ==============================================================================
-- RiskShield AI: Fraud Spike Incidents Table
-- Migration: V4__fraud_spike_incidents.sql
-- ==============================================================================

CREATE TABLE IF NOT EXISTS fraud_incidents (
    incident_id VARCHAR(64) PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('NORMAL', 'ELEVATED', 'CRITICAL')),
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    baseline_rate NUMERIC(7, 4) NOT NULL,
    current_rate NUMERIC(7, 4) NOT NULL,
    percentage_increase NUMERIC(8, 2) NOT NULL,
    affected_transactions BIGINT NOT NULL,
    estimated_exposure BIGINT NOT NULL, -- in paise
    status VARCHAR(32) DEFAULT 'OPEN' NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    time_window VARCHAR(16) DEFAULT '15m' NOT NULL,
    z_score NUMERIC(6, 2),
    explanation_summary TEXT,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_fraud_incidents_merchant ON fraud_incidents(merchant_id, status, detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_fraud_incidents_status ON fraud_incidents(status, severity);
CREATE INDEX IF NOT EXISTS idx_fraud_incidents_detected ON fraud_incidents(detected_at DESC);
