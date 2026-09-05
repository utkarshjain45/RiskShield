import time
from datetime import datetime, timezone
from fastapi import APIRouter
from app.schemas.risk import HealthResponse
from app.services.model_service import model_service
from app.core.config import settings

router = APIRouter()

@router.get(
    "/health",
    response_model=HealthResponse,
    summary="Liveness and Model Readiness Probe",
    description="Returns service uptime, container health, and model readiness status."
)
def check_health():
    uptime = round(time.time() - model_service.start_time, 2)
    return HealthResponse(
        status="UP" if model_service.is_loaded else "STARTING",
        model_loaded=model_service.is_loaded,
        model_version=model_service.get_version(),
        environment=settings.ENVIRONMENT,
        uptime_seconds=uptime,
        timestamp=datetime.now(timezone.utc).isoformat()
    )
