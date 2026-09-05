import os
import sys
import pytest
from fastapi.testclient import TestClient

# Ensure ml-service root is in sys.path
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from app.main import app
from app.services.model_service import model_service

@pytest.fixture(scope="module", autouse=True)
def init_service():
    """Ensures model artifacts are loaded prior to running API tests."""
    model_service.load_artifacts()

client = TestClient(app)

def test_get_health():
    res = client.get("/api/v1/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "UP"
    assert data["model_loaded"] is True
    assert "model_version" in data
    assert data["uptime_seconds"] >= 0

def test_root_health():
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "UP"
    assert data["model_loaded"] is True

def test_get_model_info():
    res = client.get("/api/v1/model/info")
    assert res.status_code == 200
    data = res.json()
    assert data["model_name"] == "XGBoost"
    assert data["framework"] == "xgboost"
    assert data["feature_count"] > 20
    assert "test_metrics" in data
    assert data["optimal_threshold"] > 0

def test_post_risk_score_low_risk():
    payload = {
        "transaction_id": "tx_test_normal_001",
        "merchant_id": "mer_001",
        "customer_id": "cust_001",
        "device_id": "dev_001",
        "ip_address": "103.21.124.8",
        "amount": 450.0,
        "cust_hist_avg_amount": 420.0,
        "amount_deviation": 1.07,
        "tx_count_5m": 0,
        "tx_count_30m": 0,
        "tx_count_1h": 0,
        "amount_spent_1h": 0.0,
        "cust_tx_frequency": 0.05,
        "cust_failed_rate": 0.0,
        "device_tx_count": 12,
        "device_account_count": 1,
        "ip_tx_count": 15,
        "ip_account_count": 1,
        "is_new_device": 0,
        "is_new_ip": 0,
        "customer_account_age_days": 180,
        "hour_of_day": 14,
        "day_of_week": 2,
        "payment_method": "upi",
        "merchant_category": "grocery_supermarket",
        "merchant_risk_tier": "LOW"
    }
    res = client.post("/api/v1/risk/score", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["transaction_id"] == "tx_test_normal_001"
    assert 0.0 <= data["fraud_probability"] <= 1.0
    assert 0.0 <= data["risk_score"] <= 100.0
    assert "model_version" in data
    assert isinstance(data["top_risk_signals"], list)
    assert len(data["top_risk_signals"]) > 0
    assert "inference_latency_ms" in data
    
    # Invariant: ML Service MUST NOT decide ALLOW/REVIEW/BLOCK
    assert "decision" not in data, "ML service must not decide ALLOW/REVIEW/BLOCK"
    assert "status" not in data or data.get("status") not in ["ALLOW", "REVIEW", "BLOCK"]

def test_post_risk_score_high_risk():
    payload = {
        "transaction_id": "tx_test_fraud_002",
        "merchant_id": "mer_002",
        "customer_id": "cust_002",
        "device_id": "dev_emulator_99",
        "ip_address": "185.220.101.5",
        "amount": 48000.0,
        "cust_hist_avg_amount": 500.0,
        "amount_deviation": 96.0,
        "tx_count_5m": 8,
        "tx_count_30m": 14,
        "tx_count_1h": 18,
        "amount_spent_1h": 95000.0,
        "cust_tx_frequency": 0.8,
        "cust_failed_rate": 0.5,
        "device_tx_count": 45,
        "device_account_count": 22,
        "ip_tx_count": 60,
        "ip_account_count": 28,
        "is_new_device": 1,
        "is_new_ip": 1,
        "customer_account_age_days": 12,
        "hour_of_day": 3,
        "day_of_week": 4,
        "payment_method": "card",
        "merchant_category": "electronics",
        "merchant_risk_tier": "HIGH"
    }
    res = client.post("/api/v1/risk/score", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["transaction_id"] == "tx_test_fraud_002"
    assert data["fraud_probability"] > 0.80
    assert data["risk_score"] > 80.0

def test_post_risk_score_validation_error():
    # Negative amount and invalid day_of_week
    payload = {
        "transaction_id": "tx_invalid",
        "amount": -50.0,
        "day_of_week": 99
    }
    res = client.post("/api/v1/risk/score", json=payload)
    assert res.status_code == 422
    data = res.json()
    assert data["status_code"] == 422
    assert "errors" in data

def test_post_batch_score():
    txs = [
        {
            "transaction_id": f"tx_batch_{i}",
            "amount": 250.0 * (i + 1),
            "payment_method": "upi"
        }
        for i in range(5)
    ]
    res = client.post("/api/v1/risk/batch-score", json={"transactions": txs})
    assert res.status_code == 200
    data = res.json()
    assert data["total_evaluated"] == 5
    assert len(data["results"]) == 5
    assert data["batch_latency_ms"] >= 0

def test_post_risk_explain():
    payload = {
        "transaction_id": "tx_explain_001",
        "amount": 15000.0,
        "cust_hist_avg_amount": 1200.0,
        "amount_deviation": 12.5,
        "tx_count_5m": 3,
        "tx_count_1h": 7,
        "is_new_device": 1,
        "is_new_ip": 1,
        "payment_method": "card"
    }
    res = client.post("/api/v1/risk/explain", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["transaction_id"] == "tx_explain_001"
    assert "base_value" in data
    assert isinstance(data["feature_contributions"], list)
    assert len(data["feature_contributions"]) > 0
    assert "summary_explanation" in data
    # Check that contributions have percentage and direction
    first_contrib = data["feature_contributions"][0]
    assert "shap_value" in first_contrib
    assert "direction" in first_contrib
    assert first_contrib["direction"] in ["INCREASES_RISK", "DECREASES_RISK"]
