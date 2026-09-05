from fastapi.testclient import TestClient
from app.main import app
from app.services.model_service import model_service

# Load model artifacts for test suite
model_service.load_artifacts()
client = TestClient(app)

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["service"] == "riskshield-ml-service"
    assert data["model_loaded"] is True

def test_api_v1_health():
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["model_loaded"] is True
    assert "uptime_seconds" in data
