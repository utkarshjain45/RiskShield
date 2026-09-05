-- V7: Razorpay Webhook Receipts & Idempotency Store
-- Stores webhook receipt metadata to guarantee exactly-once processing and auditability

CREATE TABLE IF NOT EXISTS razorpay_webhook_receipts (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    account_id VARCHAR(64),
    entity_id VARCHAR(64),
    amount_in_paise BIGINT,
    currency VARCHAR(16) DEFAULT 'INR',
    status VARCHAR(32) DEFAULT 'RECEIVED' NOT NULL,
    signature_verified BOOLEAN DEFAULT TRUE NOT NULL,
    payload TEXT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_rzp_webhook_entity ON razorpay_webhook_receipts(entity_id);
CREATE INDEX IF NOT EXISTS idx_rzp_webhook_event_type ON razorpay_webhook_receipts(event_type);
CREATE INDEX IF NOT EXISTS idx_rzp_webhook_received_at ON razorpay_webhook_receipts(received_at DESC);
