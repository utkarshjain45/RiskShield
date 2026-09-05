import os
from typing import Dict, Optional
import yaml
from pydantic import BaseModel, Field

class FraudWeights(BaseModel):
    velocity_fraud: float = 0.18
    new_device_abuse: float = 0.16
    device_reuse_syndicate: float = 0.14
    ip_pooling_abuse: float = 0.14
    amount_anomaly: float = 0.12
    unusual_time_abuse: float = 0.10
    abnormal_behavior: float = 0.08
    coordinated_cluster_spike: float = 0.08

class OutputDirs(BaseModel):
    raw: str = "data/raw"
    processed: str = "data/processed"
    stats: str = "data/summary_statistics.json"

class GeneratorConfig(BaseModel):
    transactions: int = Field(100000, ge=100, description="Total transactions to simulate")
    fraud_rate: float = Field(0.02, ge=0.0, le=0.5, description="Target fraud proportion")
    random_seed: int = Field(42, description="RNG seed for deterministic reproducibility")
    
    merchants: int = Field(250, ge=10, description="Unique merchant count")
    customers: int = Field(15000, ge=50, description="Unique customer count")
    devices: int = Field(22000, ge=50, description="Unique device count")
    ips: int = Field(28000, ge=50, description="Unique IP address count")
    
    start_date: str = "2026-06-01T00:00:00"
    end_date: str = "2026-08-31T23:59:59"
    
    train_ratio: float = 0.70
    val_ratio: float = 0.15
    test_ratio: float = 0.15
    
    fraud_pattern_weights: FraudWeights = Field(default_factory=FraudWeights)
    output_dirs: OutputDirs = Field(default_factory=OutputDirs)

    @classmethod
    def load_from_yaml(cls, path: Optional[str] = None) -> "GeneratorConfig":
        if path and os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                data = yaml.safe_load(f) or {}
                return cls(**data)
        # Fallback to default config.yaml if present in cwd or script dir
        candidate_paths = [
            "config.yaml",
            os.path.join(os.path.dirname(os.path.dirname(__file__)), "config.yaml"),
            "data-generator/config.yaml"
        ]
        for cp in candidate_paths:
            if os.path.exists(cp):
                with open(cp, "r", encoding="utf-8") as f:
                    data = yaml.safe_load(f) or {}
                    return cls(**data)
        return cls()
