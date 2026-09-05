# 🚀 RiskShield AI — ML Risk Scoring Service API Documentation

The **RiskShield AI ML Service** is a high-throughput, sub-millisecond FastAPI microservice that computes calibrated fraud probabilities $[0.0, 1.0]$ and normalized risk scores $[0, 100]$ using the production XGBoost champion model (`RiskShield-XGB-Fraud-v1`) and generates exact local feature attributions via TreeSHAP.

---

## 🏛️ Architectural Invariants

1. **Risk Scoring Only:** This service answers *"How risky does this transaction look?"*. It **never** issues unilateral `ALLOW`, `REVIEW`, or `BLOCK` decisions.
2. **Deterministic Enforcement Downstream:** The Spring Boot backend Policy Engine receives the `risk_score` and `top_risk_signals` and maps them deterministically against merchant policies.
3. **Loaded Once on Startup:** Models, preprocessors, schemas, and TreeSHAP explainers are loaded into memory once during FastAPI lifespan startup. No per-request disk reads or retraining.
4. **No LLMs in Critical Path:** All contributing signal rankings and explanations are computed mathematically via TreeSHAP feature attributions.

---

## 📑 Endpoints Summary

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/health` | Container liveness probe, model readiness, and uptime. |
| `GET` | `/api/v1/model/info` | Production model metadata, feature count, and held-out test set metrics. |
| `POST` | `/api/v1/risk/score` | Real-time single transaction scoring with top-5 TreeSHAP risk signals. |
| `POST` | `/api/v1/risk/batch-score` | High-throughput vectorized batch scoring for up to 500 transactions. |
| `POST` | `/api/v1/risk/explain` | Comprehensive local feature attribution breakdown via TreeSHAP. |

---

## 1. Health Probe (`GET /api/v1/health`)

### Response (`200 OK`)
```json
{
  "status": "UP",
  "model_loaded": true,
  "model_version": "v1.0.0-xgboost",
  "environment": "development",
  "uptime_seconds": 128.45,
  "timestamp": "2026-09-04T21:40:00.123456Z"
}
```

---

## 2. Model Metadata (`GET /api/v1/model/info`)

### Response (`200 OK`)
```json
{
  "model_name": "XGBoost",
  "model_version": "v1.0.0-xgboost",
  "framework": "xgboost",
  "optimal_threshold": 0.05,
  "feature_count": 32,
  "input_features": [
    "amount",
    "cust_hist_avg_amount",
    "amount_deviation",
    "tx_count_5m",
    "tx_count_30m",
    "tx_count_1h",
    "amount_spent_1h",
    "cust_tx_frequency",
    "cust_failed_rate",
    "device_tx_count",
    "device_account_count",
    "ip_tx_count",
    "ip_account_count",
    "is_new_device",
    "is_new_ip",
    "customer_account_age_days",
    "hour_of_day",
    "day_of_week",
    "payment_method_card",
    "payment_method_netbanking",
    "payment_method_upi",
    "merchant_category_digital_gaming",
    "merchant_category_electronics",
    "merchant_category_fashion_apparel",
    "merchant_category_fintech_lending",
    "merchant_category_food_dining",
    "merchant_category_grocery_supermarket",
    "merchant_category_pharmacy_healthcare",
    "merchant_category_travel_airline",
    "merchant_risk_tier_HIGH",
    "merchant_risk_tier_LOW",
    "merchant_risk_tier_MEDIUM"
  ],
  "created_at": "2026-09-04T21:31:36.054750",
  "test_metrics": {
    "threshold": 0.05,
    "precision": 0.9911,
    "recall": 1.0,
    "f1": 0.9955,
    "pr_auc": 0.999,
    "roc_auc": 1.0,
    "false_positive_rate": 0.00027,
    "false_negative_rate": 0.0,
    "confusion_matrix": {
      "true_negatives": 14552,
      "false_positives": 4,
      "false_negatives": 0,
      "true_positives": 444
    },
    "monetary_impact": {
      "fn_count": 0,
      "fn_cost_inr": 0.0,
      "fp_count": 4,
      "fp_cost_inr": 776.48,
      "total_cost_inr": 776.48
    }
  }
}
```

---

## 3. Real-Time Risk Scoring (`POST /api/v1/risk/score`)

### Request Payload
```json
{
  "transaction_id": "tx_20260904_891238",
  "merchant_id": "mer_0064",
  "customer_id": "cust_002653",
  "device_id": "dev_018565",
  "ip_address": "185.220.101.5",
  "amount": 48000.0,
  "cust_hist_avg_amount": 1200.0,
  "amount_deviation": 40.0,
  "tx_count_5m": 8,
  "tx_count_30m": 12,
  "tx_count_1h": 15,
  "amount_spent_1h": 92000.0,
  "cust_tx_frequency": 0.45,
  "cust_failed_rate": 0.35,
  "device_tx_count": 42,
  "device_account_count": 18,
  "ip_tx_count": 55,
  "ip_account_count": 24,
  "is_new_device": 1,
  "is_new_ip": 1,
  "customer_account_age_days": 45,
  "hour_of_day": 3,
  "day_of_week": 4,
  "payment_method": "card",
  "merchant_category": "electronics",
  "merchant_risk_tier": "HIGH"
}
```

### Response (`200 OK`)
```json
{
  "transaction_id": "tx_20260904_891238",
  "fraud_probability": 0.9642,
  "risk_score": 96.42,
  "model_version": "v1.0.0-xgboost",
  "top_risk_signals": [
    {
      "feature_name": "tx_count_5m",
      "feature_value": 8,
      "shap_impact": 0.412,
      "direction": "INCREASES_RISK",
      "description": "Observed velocity: 8 events (elevates risk)"
    },
    {
      "feature_name": "amount_deviation",
      "feature_value": 40.0,
      "shap_impact": 0.315,
      "direction": "INCREASES_RISK",
      "description": "Transaction amount is 40.0x of customer average (elevates risk)"
    },
    {
      "feature_name": "device_account_count",
      "feature_value": 18,
      "shap_impact": 0.284,
      "direction": "INCREASES_RISK",
      "description": "Hardware device shared across 18 distinct customer accounts (syndicate indicator)"
    },
    {
      "feature_name": "is_new_device",
      "feature_value": 1,
      "shap_impact": 0.182,
      "direction": "INCREASES_RISK",
      "description": "Transaction originates from an unrecognized hardware device"
    },
    {
      "feature_name": "hour_of_day",
      "feature_value": 3,
      "shap_impact": 0.145,
      "direction": "INCREASES_RISK",
      "description": "hour_of_day = 3 (elevates fraud risk)"
    }
  ],
  "timestamp": "2026-09-04T21:40:02.345678Z",
  "inference_latency_ms": 3.42
}
```

---

## 4. TreeSHAP Explainability (`POST /api/v1/risk/explain`)

### Request Payload
Same schema as `/risk/score`.

### Response (`200 OK`)
```json
{
  "transaction_id": "tx_20260904_891238",
  "fraud_probability": 0.9642,
  "risk_score": 96.42,
  "model_version": "v1.0.0-xgboost",
  "base_value": -3.8812,
  "feature_contributions": [
    {
      "feature_name": "tx_count_5m",
      "raw_value": 8,
      "shap_value": 0.412,
      "contribution_percentage": 28.45,
      "direction": "INCREASES_RISK"
    },
    {
      "feature_name": "amount_deviation",
      "raw_value": 40.0,
      "shap_value": 0.315,
      "contribution_percentage": 21.75,
      "direction": "INCREASES_RISK"
    },
    {
      "feature_name": "device_account_count",
      "raw_value": 18,
      "shap_value": 0.284,
      "contribution_percentage": 19.61,
      "direction": "INCREASES_RISK"
    }
  ],
  "summary_explanation": "Risk score (96.42/100) primarily driven by: tx_count_5m (+0.41), amount_deviation (+0.31), device_account_count (+0.28).",
  "timestamp": "2026-09-04T21:40:05.123456Z"
}
```

---

## 5. Batch Scoring (`POST /api/v1/risk/batch-score`)

Evaluates batches of up to 500 transactions via a single vectorized matrix inference pass.

```json
{
  "transactions": [
    { "transaction_id": "tx_batch_01", "amount": 450.0, "payment_method": "upi" },
    { "transaction_id": "tx_batch_02", "amount": 89000.0, "payment_method": "card", "tx_count_5m": 7 }
  ]
}
```
Response returns `total_evaluated`, `batch_latency_ms`, and the individual results array.

---

## 6. Error Responses

### Schema Validation Error (`422 Unprocessable Content`)
```json
{
  "detail": "Input schema validation failed",
  "errors": [
    "amount: Input should be greater than 0"
  ],
  "status_code": 422,
  "timestamp": "2026-09-04T21:40:10.000000Z"
}
```

### Internal Error (`500 Internal Server Error`)
```json
{
  "detail": "An internal ML inference error occurred",
  "error_type": "ValueError",
  "status_code": 500,
  "timestamp": "2026-09-04T21:40:12.000000Z"
}
```
