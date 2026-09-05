import logging
import sys
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.services.model_service import model_service
from app.api.v1.endpoints.health import router as health_router
from app.api.v1.endpoints.model import router as model_router
from app.api.v1.endpoints.risk import router as risk_router

# Setup structured console logging
logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("riskshield.ml")

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initializes and loads ML models and TreeSHAP explainers once on service startup."""
    logger.info("Initializing RiskShield AI ML Risk Service...")
    try:
        model_service.load_artifacts()
    except Exception as e:
        logger.error("Failed to load model artifacts on startup: %s", str(e), exc_info=True)
    yield
    logger.info("Shutting down RiskShield AI ML Risk Service...")

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description=(
        "Production-quality Machine Learning Risk Scoring & TreeSHAP Explainability microservice "
        "for RiskShield AI (Razorpay AI Buildathon). Evaluates transaction fraud probability "
        "and normalized risk score [0, 100] without unilateral enforcement decisions."
    ),
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url=f"{settings.API_V1_STR}/openapi.json"
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)

# Global Request Validation Error Handler
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    error_messages = [f"{err['loc'][-1]}: {err['msg']}" for err in exc.errors()]
    logger.warning("Input validation failure on %s: %s", request.url.path, error_messages)
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={
            "detail": "Input schema validation failed",
            "errors": error_messages,
            "status_code": 422,
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
    )

# Global Internal Error Handler
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error("Unhandled server exception on %s: %s", request.url.path, str(exc), exc_info=True)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "detail": "An internal ML inference error occurred",
            "error_type": type(exc).__name__,
            "status_code": 500,
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
    )

# Include API v1 Routers
app.include_router(health_router, prefix=settings.API_V1_STR, tags=["Health"])
app.include_router(model_router, prefix=settings.API_V1_STR, tags=["Model Info"])
app.include_router(risk_router, prefix=settings.API_V1_STR, tags=["Risk Scoring & Explainability"])

# Root /health alias for container orchestrators & Docker healthchecks
@app.get("/health", tags=["Health"], include_in_schema=False)
def root_health():
    return {
        "status": "UP" if model_service.is_loaded else "STARTING",
        "service": "riskshield-ml-service",
        "model_loaded": model_service.is_loaded,
        "model_version": model_service.get_version()
    }
