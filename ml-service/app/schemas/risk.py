from datetime import datetime, timezone
from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field, model_validator

class TransactionRiskRequest(BaseModel):
    """
    Ingestion contract containing transaction metadata and all required
    behavioral, velocity, and relational graph features.
    """
    transaction_id: str = Field(..., description="Unique transaction reference ID")
    merchant_id: Optional[str] = Field("mer_default", description="Merchant account ID")
    customer_id: Optional[str] = Field("cust_default", description="Customer account ID")
    device_id: Optional[str] = Field("dev_default", description="Device hardware fingerprint ID")
    ip_address: Optional[str] = Field("127.0.0.1", description="Client IP address")
    
    # Financial fields
    amount: float = Field(..., gt=0.0, description="Transaction amount in INR")
    cust_hist_avg_amount: Optional[float] = Field(None, description="Running mean of past amounts for customer")
    amount_deviation: Optional[float] = Field(None, description="Ratio of amount to historical average")
    
    # Velocity sliding windows
    tx_count_5m: int = Field(0, ge=0, description="Customer transactions in last 5 minutes")
    tx_count_30m: int = Field(0, ge=0, description="Customer transactions in last 30 minutes")
    tx_count_1h: int = Field(0, ge=0, description="Customer transactions in last 1 hour")
    amount_spent_1h: float = Field(0.0, ge=0.0, description="Total amount spent by customer in last 1 hour")
    
    # Historical customer aggregates
    cust_tx_frequency: float = Field(0.0, ge=0.0, description="Historical customer transaction frequency per day")
    cust_failed_rate: float = Field(0.0, ge=0.0, le=1.0, description="Historical failure rate of customer payments")
    
    # Device & IP graph multiplexing
    device_tx_count: int = Field(0, ge=0, description="Cumulative transactions on this device")
    device_account_count: int = Field(1, ge=1, description="Distinct customer accounts seen on this device")
    ip_tx_count: int = Field(0, ge=0, description="Cumulative transactions from this IP")
    ip_account_count: int = Field(1, ge=1, description="Distinct customer accounts seen on this IP")
    
    # Novelty & Context
    is_new_device: int = Field(0, ge=0, le=1, description="1 if device is novel for this customer, else 0")
    is_new_ip: int = Field(0, ge=0, le=1, description="1 if IP is novel for this customer, else 0")
    customer_account_age_days: int = Field(30, ge=1, description="Customer account age in days")
    hour_of_day: int = Field(12, ge=0, le=23, description="UTC hour of transaction (0-23)")
    day_of_week: int = Field(0, ge=0, le=6, description="Day of week (0=Monday, 6=Sunday)")
    
    # Categoricals
    payment_method: str = Field("card", description="Payment channel: card, upi, netbanking")
    merchant_category: str = Field("grocery_supermarket", description="Merchant commercial category")
    merchant_risk_tier: str = Field("MEDIUM", description="Merchant risk tier: LOW, MEDIUM, HIGH")
    
    timestamp: Optional[str] = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    @model_validator(mode="after")
    def populate_defaults(self) -> "TransactionRiskRequest":
        if self.cust_hist_avg_amount is None:
            self.cust_hist_avg_amount = self.amount
        if self.amount_deviation is None:
            denom = self.cust_hist_avg_amount if self.cust_hist_avg_amount > 0 else 1.0
            self.amount_deviation = round(self.amount / (denom + 1e-4), 4)
        return self

class RiskSignal(BaseModel):
    feature_name: str = Field(..., description="Engineered feature key")
    feature_value: Any = Field(..., description="Current raw value of feature")
    shap_impact: float = Field(..., description="SHAP attribution impact on fraud log-odds")
    direction: str = Field(..., description="INCREASES_RISK or DECREASES_RISK")
    description: str = Field(..., description="Human-readable business explanation of signal")
    display_name: Optional[str] = Field(None, description="Merchant-friendly display label")
    formatted_impact: Optional[str] = Field(None, description="Formatted signed attribution impact (e.g. +0.27)")

class RiskScoreResponse(BaseModel):
    transaction_id: str
    fraud_probability: float = Field(..., ge=0.0, le=1.0, description="Raw calibrated fraud probability [0.0, 1.0]")
    risk_score: float = Field(..., ge=0.0, le=100.0, description="Normalized risk score [0, 100]")
    model_version: str = Field(..., description="Active production model version identifier")
    top_risk_signals: List[RiskSignal] = Field(..., description="Top contributing signals driving the score")
    timestamp: str = Field(..., description="Evaluation timestamp (ISO-8601 UTC)")
    inference_latency_ms: float = Field(..., description="Inference latency in milliseconds")

class BatchRiskScoreRequest(BaseModel):
    transactions: List[TransactionRiskRequest] = Field(..., min_length=1, max_length=500)

class BatchRiskScoreResponse(BaseModel):
    total_evaluated: int
    model_version: str
    results: List[RiskScoreResponse]
    batch_latency_ms: float

class FeatureContribution(BaseModel):
    feature_name: str
    raw_value: Any
    shap_value: float
    contribution_percentage: float
    direction: str
    display_name: Optional[str] = None
    formatted_impact: Optional[str] = None

class ExplainResponse(BaseModel):
    transaction_id: str
    fraud_probability: float
    risk_score: float
    model_version: str
    base_value: float = Field(..., description="Baseline expected value of the model")
    feature_contributions: List[FeatureContribution]
    summary_explanation: str
    timestamp: str

class ModelInfoResponse(BaseModel):
    model_name: str
    model_version: str
    framework: str
    optimal_threshold: float
    feature_count: int
    input_features: List[str]
    created_at: str
    test_metrics: Dict[str, Any]

class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_version: str
    environment: str
    uptime_seconds: float
    timestamp: str

class ErrorResponse(BaseModel):
    detail: str
    status_code: int
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat())
