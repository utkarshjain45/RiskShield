import os
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    PROJECT_NAME: str = "RiskShield AI - ML Risk Scoring Service"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # Environment & Logging
    ENVIRONMENT: str = "development"
    LOG_LEVEL: str = "INFO"
    
    # Model Artifact Paths (relative to ml-service root)
    ML_SERVICE_DIR: str = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    MODELS_DIR: str = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "models")
    
    MODEL_FILE: str = "best_model.joblib"
    PREPROCESSOR_FILE: str = "preprocessor.joblib"
    SCHEMA_FILE: str = "feature_schema.json"
    METADATA_FILE: str = "model_metadata.json"

    # Default model version fallback
    DEFAULT_MODEL_VERSION: str = "v1.0.0-xgboost"

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

settings = Settings()
