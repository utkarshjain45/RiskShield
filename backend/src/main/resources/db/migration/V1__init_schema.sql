-- ==============================================================================
-- RiskShield AI: Initial PostgreSQL Relational Schema
-- Migration: V1__init_schema.sql
-- ==============================================================================

-- 1. Merchants Table
CREATE TABLE IF NOT EXISTS merchants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    category VARCHAR(64) DEFAULT 'grocery_supermarket' NOT NULL,
    risk_tier VARCHAR(16) DEFAULT 'MEDIUM' NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_merchants_active ON merchants(active);

-- 2. Customers Table
CREATE TABLE IF NOT EXISTS customers (
    id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(255),
    contact VARCHAR(32),
    billing_state VARCHAR(64),
    risk_segment VARCHAR(32) DEFAULT 'standard' NOT NULL,
    account_created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_customers_created_at ON customers(created_at);

-- 3. Devices Table
CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(64) PRIMARY KEY,
    device_type VARCHAR(64) DEFAULT 'mobile_android' NOT NULL,
    is_emulator BOOLEAN DEFAULT FALSE NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 4. Transactions Table
CREATE TABLE IF NOT EXISTS transactions (
    id VARCHAR(64) PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    customer_id VARCHAR(64) REFERENCES customers(id) ON DELETE SET NULL,
    device_id VARCHAR(64) REFERENCES devices(id) ON DELETE SET NULL,
    ip_address VARCHAR(45),
    amount_in_paise BIGINT NOT NULL,
    currency VARCHAR(8) DEFAULT 'INR' NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
    customer_account_age_days INT DEFAULT 30 NOT NULL,
    is_new_device BOOLEAN DEFAULT FALSE NOT NULL,
    is_new_ip BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_transactions_merchant ON transactions(merchant_id, created_at DESC);
CREATE INDEX idx_transactions_customer ON transactions(customer_id, created_at DESC);
CREATE INDEX idx_transactions_device ON transactions(device_id);
CREATE INDEX idx_transactions_ip ON transactions(ip_address);
CREATE INDEX idx_transactions_created ON transactions(created_at DESC);

-- 5. Model Versions Table
CREATE TABLE IF NOT EXISTS model_versions (
    id VARCHAR(64) PRIMARY KEY,
    version_name VARCHAR(64) UNIQUE NOT NULL,
    framework VARCHAR(32) DEFAULT 'xgboost' NOT NULL,
    optimal_threshold NUMERIC(5, 4) DEFAULT 0.0500 NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 6. Risk Assessments Table
CREATE TABLE IF NOT EXISTS risk_assessments (
    id VARCHAR(64) PRIMARY KEY,
    transaction_id VARCHAR(64) UNIQUE NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    model_version VARCHAR(64) NOT NULL,
    fraud_probability NUMERIC(7, 5) NOT NULL,
    risk_score NUMERIC(5, 2) NOT NULL,
    inference_latency_ms NUMERIC(8, 2) DEFAULT 0.0 NOT NULL,
    prediction_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_risk_assessments_tx ON risk_assessments(transaction_id);
CREATE INDEX idx_risk_assessments_score ON risk_assessments(risk_score);

-- 7. Risk Signals Table (TreeSHAP features & behavioral triggers)
CREATE TABLE IF NOT EXISTS risk_signals (
    id BIGSERIAL PRIMARY KEY,
    assessment_id VARCHAR(64) NOT NULL REFERENCES risk_assessments(id) ON DELETE CASCADE,
    signal_name VARCHAR(128) NOT NULL,
    signal_value VARCHAR(255),
    shap_impact NUMERIC(8, 4),
    direction VARCHAR(32) DEFAULT 'INCREASES_RISK' NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_risk_signals_assessment ON risk_signals(assessment_id);

-- 8. Risk Policies Table
CREATE TABLE IF NOT EXISTS risk_policies (
    id VARCHAR(64) PRIMARY KEY,
    merchant_id VARCHAR(64) REFERENCES merchants(id) ON DELETE CASCADE,
    policy_version VARCHAR(32) NOT NULL,
    block_threshold NUMERIC(5, 2) DEFAULT 85.00 NOT NULL,
    review_threshold NUMERIC(5, 2) DEFAULT 60.00 NOT NULL,
    max_single_tx_paise BIGINT DEFAULT 10000000, -- 1 Lakh INR
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_risk_policies_merchant ON risk_policies(merchant_id, active);

-- 9. Risk Decisions Table (Deterministic ALLOW / REVIEW / BLOCK)
CREATE TABLE IF NOT EXISTS risk_decisions (
    id VARCHAR(64) PRIMARY KEY,
    transaction_id VARCHAR(64) UNIQUE NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    risk_score NUMERIC(5, 2) NOT NULL,
    decision VARCHAR(16) NOT NULL CHECK (decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    policy_version VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_risk_decisions_tx ON risk_decisions(transaction_id);
CREATE INDEX idx_risk_decisions_decision ON risk_decisions(decision, created_at DESC);

-- 10. Alerts Table (Velocity spikes and critical fraud alarms)
CREATE TABLE IF NOT EXISTS alerts (
    id VARCHAR(64) PRIMARY KEY,
    transaction_id VARCHAR(64) REFERENCES transactions(id) ON DELETE SET NULL,
    merchant_id VARCHAR(64) REFERENCES merchants(id) ON DELETE CASCADE,
    alert_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) DEFAULT 'HIGH' NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    status VARCHAR(32) DEFAULT 'OPEN' NOT NULL,   -- OPEN, ACKNOWLEDGED, RESOLVED
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_alerts_merchant_status ON alerts(merchant_id, status, created_at DESC);
CREATE INDEX idx_alerts_tx ON alerts(transaction_id);

-- 11. Audit Events Table (Immutable append-only ledger)
CREATE TABLE IF NOT EXISTS audit_events (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) REFERENCES transactions(id) ON DELETE CASCADE,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    correlation_id VARCHAR(64),
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE INDEX idx_audit_events_tx ON audit_events(transaction_id);
CREATE INDEX idx_audit_events_correlation ON audit_events(correlation_id);
CREATE INDEX idx_audit_events_created ON audit_events(created_at DESC);
