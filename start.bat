@echo off
REM ==============================================================================
REM RiskShield AI — One-Command Startup Script (Windows Batch)
REM ==============================================================================

echo =================================================================
echo   RiskShield AI - Production Risk Management Platform
echo =================================================================

if not exist .env (
    echo Creating .env from .env.example...
    copy .env.example .env >nul
)

docker compose up --build -d

echo.
echo =================================================================
echo   RiskShield AI is starting!
echo =================================================================
echo   Operations Console:   http://localhost:3000
echo   Backend REST API:     http://localhost:8080/api/v1
echo   Swagger / OpenAPI:    http://localhost:8080/swagger-ui/index.html
echo   Python ML Service:    http://localhost:8000/health
echo   Actuator Health:      http://localhost:8080/actuator/health
echo.
echo   5-Minute Demo Script: View docs/DEMO_SCRIPT.md
echo   Track 2 Alignment:   View docs/JUDGING.md
echo =================================================================
