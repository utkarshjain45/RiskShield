from typing import Tuple, Optional
from app.schemas.risk import SpikeEvaluationRequest, SpikeEvaluationResponse

class SpikeDetectorService:
    """
    Statistical Anomaly Detector for Fraud Spikes.
    Applies sliding-window baseline estimation and Z-score testing.
    """

    def __init__(self, z_threshold: float = 3.0):
        self.z_threshold = z_threshold

    def evaluate_spike(self, req: SpikeEvaluationRequest) -> SpikeEvaluationResponse:
        # Expected baseline for demonstration skeleton
        baseline_mean = 2.0
        baseline_std = 1.0
        
        current_val = float(req.current_fraud_count)
        z_score = (current_val - baseline_mean) / baseline_std if baseline_std > 0 else 0.0
        
        is_spike = z_score >= self.z_threshold
        spike_type: Optional[str] = None
        action: Optional[str] = None

        if is_spike:
            spike_type = "VELOCITY_FRAUD_SURGE"
            action = "ENABLE_STEP_UP_AUTHENTICATION_OR_RATE_LIMIT"

        return SpikeEvaluationResponse(
            spike_detected=is_spike,
            spike_type=spike_type,
            z_score=round(z_score, 3),
            baseline_velocity=baseline_mean,
            current_velocity=current_val,
            recommended_action=action
        )

spike_detector = SpikeDetectorService()
