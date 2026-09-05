-- ==============================================================================
-- RiskShield AI: Signal Display Fields for Normalized SHAP Explainability
-- Migration: V5__add_signal_display_fields.sql
-- ==============================================================================

ALTER TABLE risk_signals ADD COLUMN IF NOT EXISTS display_name VARCHAR(128);
ALTER TABLE risk_signals ADD COLUMN IF NOT EXISTS formatted_impact VARCHAR(32);
