package com.riskshield.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAnalyticsSummaryDto {

    private long totalTransactions;
    private long totalDecisions;
    private Map<String, Long> decisionBreakdown;
    private long openAlertsCount;
}
