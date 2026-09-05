-- V6: Immutable Model Evaluation Runs on Held-Out Test Set
-- Strictly read-only evaluation benchmark records to prevent test set data leakage

CREATE TABLE IF NOT EXISTS model_evaluation_runs (
    evaluation_id VARCHAR(64) PRIMARY KEY,
    model_version VARCHAR(64) NOT NULL,
    dataset_version VARCHAR(64) NOT NULL,
    test_set_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    metrics TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_model_eval_runs_version ON model_evaluation_runs(model_version);
CREATE INDEX IF NOT EXISTS idx_model_eval_runs_created ON model_evaluation_runs(created_at DESC);

-- Seed immutable baseline evaluation record for production XGBoost champion on held-out test set (15,000 transactions)
INSERT INTO model_evaluation_runs (
    evaluation_id,
    model_version,
    dataset_version,
    test_set_version,
    created_at,
    metrics
)
SELECT
    'eval_run_heldout_v1_0_0',
    'v1.0.0-xgboost',
    'v1.0-synthetic-creditcard',
    'test-set-v1.0-heldout',
    CURRENT_TIMESTAMP,
    '{"dataset_size":15000,"fraud_count":444,"non_fraud_count":14556,"precision":0.99107,"recall":1.0,"f1":0.99552,"roc_auc":0.99997,"pr_auc":0.99899,"false_positive_rate":0.00027,"false_negative_rate":0.0,"confusion_matrix":{"true_negatives":14552,"false_positives":4,"false_negatives":0,"true_positives":444},"false_positive_cost":776.48,"false_negative_cost":0.0,"estimated_prevented_loss":2508888.61}'
WHERE NOT EXISTS (SELECT 1 FROM model_evaluation_runs WHERE evaluation_id = 'eval_run_heldout_v1_0_0');
