-- ==============================================================================
-- RiskShield AI: Demo Simulation Sessions
-- Migration: V10__demo_simulations.sql
-- ==============================================================================

CREATE TABLE IF NOT EXISTS demo_simulations (
    id VARCHAR(64) PRIMARY KEY,
    mode VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    target_count INT NOT NULL,
    generated_count INT DEFAULT 0 NOT NULL,
    allowed_count INT DEFAULT 0 NOT NULL,
    review_count INT DEFAULT 0 NOT NULL,
    blocked_count INT DEFAULT 0 NOT NULL,
    interval_ms INT DEFAULT 300 NOT NULL,
    active_incident_id VARCHAR(64),
    summary TEXT,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    stopped_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_demo_simulations_merchant ON demo_simulations(merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_demo_simulations_status ON demo_simulations(status);
