import time
from typing import Tuple, List
from app.schemas.risk import FeatureVector, ContributingSignal
from app.core.config import settings

class FraudDetectorService:
    """
    ML Fraud Inference Service Contract.
    Houses model loading, batch/real-time inference, and feature preprocessing.
    """

    def __init__(self):
        self.model_version = settings.MODEL_VERSION

    def predict(self, transaction_id: str, features: FeatureVector) -> Tuple[float, List[ContributingSignal], float]:
        start_time = time.perf_counter()
        
        # Baseline model inference contract skeleton
        # Note: Actual trained XGBoost weights will be loaded in the ML training phase
        risk_score = 0.08
        contributing_signals: List[ContributingSignal] = []

        if features.velocity_tx_5m and features.velocity_tx_5m > 5:
            risk_score += 0.35
            contributing_signals.append(
                ContributingSignal(
                    signal_name="velocity_tx_5m",
                    signal_value=float(features.velocity_tx_5m),
                    shap_impact=0.35,
                    description=f"Elevated velocity: {features.velocity_tx_5m} transactions in 5 minutes"
                )
            )

        if features.amount_in_paise > 10000000: # > 1 Lakh INR
            risk_score = min(1.0, risk_score + 0.25)
            contributing_signals.append(
                ContributingSignal(
                    signal_name="amount_in_paise",
                    signal_value=float(features.amount_in_paise),
                    shap_impact=0.25,
                    description="Unusually high transaction value exceeding typical threshold"
                )
            )

        latency_ms = (time.perf_counter() - start_time) * 1000
        return min(1.0, risk_score), contributing_signals, round(latency_ms, 2)

fraud_detector = FraudDetectorService()
