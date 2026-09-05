-- ==============================================================================
-- RiskShield AI: Policy Engine Enhancements
-- Migration: V2__policy_engine_enhancements.sql
-- ==============================================================================

-- 1. Enhance risk_policies table
ALTER TABLE risk_policies ADD COLUMN IF NOT EXISTS name VARCHAR(128) DEFAULT 'Standard Risk Policy' NOT NULL;
ALTER TABLE risk_policies ADD COLUMN IF NOT EXISTS low_risk_threshold NUMERIC(5, 2) DEFAULT 30.00 NOT NULL;
ALTER TABLE risk_policies ADD COLUMN IF NOT EXISTS false_positive_cost_weight NUMERIC(4, 2) DEFAULT 1.00 NOT NULL;
ALTER TABLE risk_policies ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE NOT NULL;

-- Update defaults for existing rows to align with 0-30 ALLOW, 31-89 REVIEW, 90-100 BLOCK
UPDATE risk_policies
SET low_risk_threshold = 30.00,
    review_threshold = 70.00,
    block_threshold = 90.00
WHERE merchant_id IS NULL;

-- Seed global default risk policy (0-30 ALLOW, 31-89 REVIEW, 90-100 BLOCK)
INSERT INTO risk_policies (id, merchant_id, name, policy_version, low_risk_threshold, review_threshold, block_threshold, false_positive_cost_weight, max_single_tx_paise, enabled, created_at)
SELECT 'pol_default_baseline', NULL, 'Global Default Risk Policy', 'v1.0.0', 30.00, 70.00, 90.00, 1.00, 10000000, TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM risk_policies WHERE id = 'pol_default_baseline');

-- 2. Enhance risk_decisions table with policy linkage and reason
ALTER TABLE risk_decisions ADD COLUMN IF NOT EXISTS policy_id VARCHAR(64) REFERENCES risk_policies(id) ON DELETE SET NULL;
ALTER TABLE risk_decisions ADD COLUMN IF NOT EXISTS decision_reason TEXT;
ALTER TABLE risk_decisions ADD COLUMN IF NOT EXISTS evaluated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL;

-- Backfill decision_reason from existing reason if null
UPDATE risk_decisions SET decision_reason = reason WHERE decision_reason IS NULL;

-- 3. Create risk_policy_rules table for extensible rule engine
CREATE TABLE IF NOT EXISTS risk_policy_rules (
    id VARCHAR(64) PRIMARY KEY,
    policy_id VARCHAR(64) NOT NULL REFERENCES risk_policies(id) ON DELETE CASCADE,
    rule_name VARCHAR(128) NOT NULL,
    rule_type VARCHAR(64) DEFAULT 'THRESHOLD' NOT NULL,
    condition_operator VARCHAR(32) NOT NULL,
    condition_value VARCHAR(128) NOT NULL,
    action VARCHAR(16) NOT NULL CHECK (action IN ('ALLOW', 'REVIEW', 'BLOCK')),
    priority INT DEFAULT 100 NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_policy_rules_policy ON risk_policy_rules(policy_id, priority ASC);
