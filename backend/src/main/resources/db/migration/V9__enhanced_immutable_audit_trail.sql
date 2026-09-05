-- ==============================================================================
-- RiskShield AI: Enhanced Immutable Audit Trail
-- Migration: V9__enhanced_immutable_audit_trail.sql
-- ==============================================================================

ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS audit_id VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS event_type VARCHAR(64) DEFAULT 'TRANSACTION_RECEIVED' NOT NULL;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS actor_type VARCHAR(32) DEFAULT 'SYSTEM' NOT NULL;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS merchant_id VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS service VARCHAR(64) DEFAULT 'backend-api' NOT NULL;
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS metadata TEXT;

-- Backfill audit_id for existing records if null
UPDATE audit_events 
SET audit_id = 'aud_' || id
WHERE audit_id IS NULL;

-- Backfill merchant_id from associated transactions if possible
UPDATE audit_events
SET merchant_id = (SELECT t.merchant_id FROM transactions t WHERE t.id = audit_events.transaction_id)
WHERE merchant_id IS NULL AND transaction_id IS NOT NULL;

-- Ensure audit_id is unique
CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_events_audit_id ON audit_events(audit_id);

-- Create optimized query indexes for filtering and chronological timelines
CREATE INDEX IF NOT EXISTS idx_audit_events_merchant ON audit_events(merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_event_type ON audit_events(event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_service ON audit_events(service, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_entity ON audit_events(entity_type, entity_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_audit_events_tx_chronological ON audit_events(transaction_id, created_at ASC);
