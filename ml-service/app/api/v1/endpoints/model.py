from fastapi import APIRouter, HTTPException
from app.schemas.risk import ModelInfoResponse
from app.services.model_service import model_service

router = APIRouter()

@router.get(
    "/model/info",
    response_model=ModelInfoResponse,
    summary="Active Model Architecture & Evaluation Metadata",
    description="Returns metadata about active production fraud model, input features, and test set metrics."
)
def get_model_information():
    if not model_service.is_loaded:
        raise HTTPException(status_code=503, detail="Model service is not loaded yet")
    return model_service.get_model_info()
