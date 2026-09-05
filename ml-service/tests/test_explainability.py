import os
import sys
import pytest

current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from app.services.model_service import model_service
from app.services.explainability import ShapExplainabilityNormalizer
from app.schemas.risk import TransactionRiskRequest

@pytest.fixture(scope="module", autouse=True)
def init_service():
    model_service.load_artifacts()

def test_explainability_normalizer_formatting():
    normalizer = ShapExplainabilityNormalizer()
    assert normalizer.format_impact(0.271) == "+0.27"
    assert normalizer.format_impact(-0.142) == "-0.14"
    assert normalizer.format_impact(0.0) == "+0.00"

def test_explainability_normalizer_aggregation():
    normalizer = ShapExplainabilityNormalizer()
    feature_names = [
        "tx_count_5m",
        "tx_count_30m",
        "amount_deviation",
        "is_new_device",
        "device_account_count",
        "unknown_custom_feat"
    ]
    shap_values = [0.15, 0.12, 0.18, 0.14, 0.19, 0.05]
    raw_tx_values = {"tx_count_5m": 2, "tx_count_30m": 5, "amount_deviation": 2.5, "is_new_device": 1, "device_account_count": 3}

    normalized = normalizer.aggregate_shap_contributions(feature_names, shap_values, raw_tx_values)
    
    # Check that tx_count_5m and tx_count_30m got aggregated into transaction_velocity (+0.27)
    velocity_sig = next((s for s in normalized if s["concept_key"] == "transaction_velocity"), None)
    assert velocity_sig is not None
    assert velocity_sig["display_name"] == "Transaction Velocity"
    assert velocity_sig["shap_impact"] == 0.27
    assert velocity_sig["formatted_impact"] == "+0.27"
    assert velocity_sig["direction"] == "INCREASES_RISK"

    # Check device_account_count
    dev_sig = next((s for s in normalized if s["concept_key"] == "device_account_count"), None)
    assert dev_sig is not None
    assert dev_sig["shap_impact"] == 0.19
    assert dev_sig["formatted_impact"] == "+0.19"

    # Check amount_deviation
    amt_sig = next((s for s in normalized if s["concept_key"] == "amount_deviation"), None)
    assert amt_sig is not None
    assert amt_sig["shap_impact"] == 0.18
    assert amt_sig["formatted_impact"] == "+0.18"

    # Sorted by descending absolute impact
    impacts = [abs(s["shap_impact"]) for s in normalized]
    assert impacts == sorted(impacts, reverse=True)

def test_score_single_explainability_determinism():
    tx = TransactionRiskRequest(
        transaction_id="tx_det_test_001",
        amount=18500.0,
        cust_hist_avg_amount=1500.0,
        amount_deviation=12.33,
        tx_count_5m=4,
        tx_count_30m=8,
        tx_count_1h=12,
        amount_spent_1h=35000.0,
        cust_tx_frequency=15.0,
        cust_failed_rate=0.4,
        device_tx_count=10,
        device_account_count=4,
        ip_tx_count=10,
        ip_account_count=3,
        is_new_device=1,
        is_new_ip=1,
        customer_account_age_days=2,
        hour_of_day=3,
        day_of_week=6,
        payment_method="card"
    )

    # Run scoring 5 times
    results = [model_service.score_single(tx) for _ in range(5)]

    first_score = results[0]
    for r in results[1:]:
        assert r.risk_score == first_score.risk_score
        assert r.fraud_probability == first_score.fraud_probability
        assert len(r.top_risk_signals) == len(first_score.top_risk_signals)
        for sig1, sig2 in zip(first_score.top_risk_signals, r.top_risk_signals):
            assert sig1.feature_name == sig2.feature_name
            assert sig1.display_name == sig2.display_name
            assert sig1.formatted_impact == sig2.formatted_impact
            assert sig1.direction == sig2.direction
            assert sig1.shap_impact == pytest.approx(sig2.shap_impact, abs=1e-5)

def test_explain_transaction_deterministic():
    tx = TransactionRiskRequest(
        transaction_id="tx_det_explain_002",
        amount=7500.0,
        cust_hist_avg_amount=1000.0,
        amount_deviation=7.5,
        tx_count_5m=2,
        is_new_device=1,
        device_account_count=3,
        customer_account_age_days=10
    )

    exp1 = model_service.explain_transaction(tx)
    exp2 = model_service.explain_transaction(tx)

    assert exp1.transaction_id == exp2.transaction_id
    assert exp1.base_value == pytest.approx(exp2.base_value, abs=1e-5)
    assert len(exp1.feature_contributions) == len(exp2.feature_contributions)
    for c1, c2 in zip(exp1.feature_contributions, exp2.feature_contributions):
        assert c1.feature_name == c2.feature_name
        assert c1.display_name == c2.display_name
        assert c1.formatted_impact == c2.formatted_impact
        assert c1.shap_value == pytest.approx(c2.shap_value, abs=1e-5)
        assert c1.direction == c2.direction
