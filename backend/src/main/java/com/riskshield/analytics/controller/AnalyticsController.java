package com.riskshield.analytics.controller;

import com.riskshield.analytics.dto.RiskAnalyticsSummaryDto;
import com.riskshield.analytics.service.AnalyticsService;
import com.riskshield.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Risk metrics, decision distributions, and anomaly aggregates")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "Get high-level summary of transactions, decisions, and open alerts")
    public ResponseEntity<ApiResponse<RiskAnalyticsSummaryDto>> getSummary() {
        RiskAnalyticsSummaryDto summary = analyticsService.getSummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get real-time operational dashboard KPIs")
    public ResponseEntity<ApiResponse<com.riskshield.analytics.dto.DashboardMetricsDto>> getDashboardMetrics() {
        com.riskshield.analytics.dto.DashboardMetricsDto metrics = analyticsService.getDashboardMetrics();
        return ResponseEntity.ok(ApiResponse.ok(metrics));
    }

    @GetMapping("/charts")
    @Operation(summary = "Get aggregated analytics charts for volume, fraud rates, and risk distributions")
    public ResponseEntity<ApiResponse<com.riskshield.analytics.dto.AnalyticsChartsDto>> getCharts() {
        com.riskshield.analytics.dto.AnalyticsChartsDto charts = analyticsService.getCharts();
        return ResponseEntity.ok(ApiResponse.ok(charts));
    }

    @GetMapping("/model-evaluation")
    @Operation(summary = "Get active ML fraud detection model test performance metrics and confusion matrix")
    public ResponseEntity<ApiResponse<com.riskshield.analytics.dto.ModelEvaluationDto>> getModelEvaluation() {
        com.riskshield.analytics.dto.ModelEvaluationDto modelEval = analyticsService.getModelEvaluation();
        return ResponseEntity.ok(ApiResponse.ok(modelEval));
    }
}
