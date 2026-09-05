#!/usr/bin/env bash
# ==============================================================================
# RiskShield AI — One-Command Startup Script (Linux / macOS)
# ==============================================================================

set -e

echo "================================================================="
echo "  🛡️  RiskShield AI — Production Risk Management Platform"
echo "================================================================="

# 1. Environment file check
if [ ! -f .env ]; then
    echo "⚙️  .env not found. Creating from .env.example..."
    cp .env.example .env
fi

# 2. Check Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Error: Docker is not installed or not in PATH."
    exit 1
fi

echo "🐳 Starting RiskShield AI infrastructure & services via Docker Compose..."
docker compose up --build -d

echo ""
echo "⏳ Waiting for services to become healthy..."
echo "-----------------------------------------------------------------"

# Poll backend health
MAX_RETRIES=30
RETRY=0
until curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1 || [ $RETRY -eq $MAX_RETRIES ]; do
    echo "   ... waiting for Backend API to become healthy ($((RETRY+1))/$MAX_RETRIES)"
    sleep 3
    RETRY=$((RETRY+1))
done

if [ $RETRY -eq $MAX_RETRIES ]; then
    echo "⚠️  Warning: Backend took longer than expected. Check 'docker compose logs backend'."
else
    echo "✅ Backend API is healthy!"
fi

echo ""
echo "================================================================="
echo "  🚀 RiskShield AI is LIVE and READY FOR EVALUATION!"
echo "================================================================="
echo ""
echo "  🌐 Operations Console:   http://localhost:3000"
echo "  🔌 Backend REST API:     http://localhost:8080/api/v1"
echo "  📖 Swagger / OpenAPI:    http://localhost:8080/swagger-ui/index.html"
echo "  🧠 Python ML Service:    http://localhost:8000/health"
echo "  📊 Actuator Health:      http://localhost:8080/actuator/health"
echo ""
echo "  📋 5-Minute Demo Script: View docs/DEMO_SCRIPT.md"
echo "  🏆 Track 2 Alignment:   View docs/JUDGING.md"
echo "================================================================="
