import logging
from fastapi import APIRouter, HTTPException, status
from app.schemas.risk import (
    TransactionRiskRequest,
    RiskScoreResponse,
    BatchRiskScoreRequest,
    BatchRiskScoreResponse,
    ExplainResponse
)
from app.services.model_service import model_service

logger = logging.getLogger("riskshield.ml.endpoints.risk")
router = APIRouter()

@router.post(
    "/risk/score",
    response_model=RiskScoreResponse,
    status_code=status.HTTP_200_OK,
    summary="Evaluate Real-Time Payment Transaction Risk",
    description=(
        "Scores transaction fraud probability [0.0, 1.0] and normalized risk score [0, 100] "
        "using active XGBoost model and returns top contributing TreeSHAP risk signals. "
        "NOTE: This endpoint answers 'How risky does this transaction look?' and never issues "
        "direct ALLOW/BLOCK enforcement decisions."
    )
)
def score_transaction(tx: TransactionRiskRequest):
    if not model_service.is_loaded:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Model is currently initializing or unavailable"
        )
    try:
        response = model_service.score_single(tx)
        logger.info(
            "Scored transaction %s | Prob: %.4f | Score: %.2f | Latency: %.2fms",
            tx.transaction_id,
            response.fraud_probability,
            response.risk_score,
            response.inference_latency_ms
        )
        return response
    except Exception as e:
        logger.error("Failed to score transaction %s: %s", tx.transaction_id, str(e), exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Inference error scoring transaction: {str(e)}"
        )

@router.post(
    "/risk/batch-score",
    response_model=BatchRiskScoreResponse,
    status_code=status.HTTP_200_OK,
    summary="High-Throughput Vectorized Batch Risk Scoring",
    description="Vectorized scoring for batches up to 500 payment transactions."
)
def batch_score_transactions(req: BatchRiskScoreRequest):
    if not model_service.is_loaded:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Model is currently initializing or unavailable"
        )
    try:
        response = model_service.score_batch(req)
        logger.info(
            "Batch scored %d transactions in %.2fms",
            response.total_evaluated,
            response.batch_latency_ms
        )
        return response
    except Exception as e:
        logger.error("Failed to batch score transactions: %s", str(e), exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Batch inference error: {str(e)}"
        )

@router.post(
    "/risk/explain",
    response_model=ExplainResponse,
    status_code=status.HTTP_200_OK,
    summary="TreeSHAP Feature Attribution & Local Explainability",
    description=(
        "Calculates exact local feature attributions (TreeSHAP) explaining how each behavioral "
        "feature pushed the transaction score higher or lower relative to base rate."
    )
)
def explain_transaction(tx: TransactionRiskRequest):
    if not model_service.is_loaded:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Model is currently initializing or unavailable"
        )
    try:
        response = model_service.explain_transaction(tx)
        logger.info(
            "Explained transaction %s | Top Drivers: %s",
            tx.transaction_id,
            response.summary_explanation
        )
        return response
    except Exception as e:
        logger.error("Failed to explain transaction %s: %s", tx.transaction_id, str(e), exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Explainability error: {str(e)}"
        )
