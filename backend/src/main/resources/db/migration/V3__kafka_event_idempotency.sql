-- ==============================================================================
-- RiskShield AI: Kafka Event Idempotency Store
-- Migration: V3__kafka_event_idempotency.sql
-- ==============================================================================

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(64),
    correlation_id VARCHAR(64),
    consumer_group VARCHAR(64) DEFAULT 'riskshield-backend-group' NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_processed_events_tx ON processed_events(transaction_id);
CREATE INDEX IF NOT EXISTS idx_processed_events_type ON processed_events(event_type);
CREATE INDEX IF NOT EXISTS idx_processed_events_time ON processed_events(processed_at DESC);
