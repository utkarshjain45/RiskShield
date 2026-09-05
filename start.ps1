# ==============================================================================
# RiskShield AI — One-Command Startup Script (Windows PowerShell)
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  🛡️  RiskShield AI — Production Risk Management Platform" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Environment check
if (-Not (Test-Path ".env")) {
    Write-Host "⚙️  .env not found. Creating from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
}

# 2. Docker check
if (-Not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Error: Docker is not installed or not in PATH." -ForegroundColor Red
    Exit 1
}

Write-Host "🐳 Starting RiskShield AI infrastructure & services via Docker Compose..." -ForegroundColor Green
docker compose up --build -d

Write-Host ""
Write-Host "⏳ Waiting for services to become healthy..." -ForegroundColor Yellow
Write-Host "-----------------------------------------------------------------"

$maxRetries = 30
$retry = 0
$isHealthy = $false

while ($retry -lt $maxRetries -and -not $isHealthy) {
    Start-Sleep -Seconds 3
    $retry++
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            $isHealthy = $true
        }
    } catch {
        Write-Host "   ... waiting for Backend API to become healthy ($retry/$maxRetries)"
    }
}

if ($isHealthy) {
    Write-Host "✅ Backend API is healthy!" -ForegroundColor Green
} else {
    Write-Host "⚠️  Warning: Backend took longer than expected. Check 'docker compose logs backend'." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  🚀 RiskShield AI is LIVE and READY FOR EVALUATION!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  🌐 Operations Console:   http://localhost:3000" -ForegroundColor White
Write-Host "  🔌 Backend REST API:     http://localhost:8080/api/v1" -ForegroundColor White
Write-Host "  📖 Swagger / OpenAPI:    http://localhost:8080/swagger-ui/index.html" -ForegroundColor White
Write-Host "  🧠 Python ML Service:    http://localhost:8000/health" -ForegroundColor White
Write-Host "  📊 Actuator Health:      http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host ""
Write-Host "  📋 5-Minute Demo Script: View docs/DEMO_SCRIPT.md" -ForegroundColor Yellow
Write-Host "  🏆 Track 2 Alignment:   View docs/JUDGING.md" -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan
