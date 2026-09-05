package com.riskshield.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riskshield.audit.dto.AuditEventResponse;
import com.riskshield.risk.dto.RiskExplanationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionInvestigationDto {

    private TransactionResponse transaction;
    private RiskExplanationResponse explanation;

    @JsonProperty("customer_summary")
    private CustomerSummaryDto customerSummary;

    @JsonProperty("device_summary")
    private DeviceSummaryDto deviceSummary;

    @JsonProperty("ip_summary")
    private IpSummaryDto ipSummary;

    @JsonProperty("related_transactions")
    private List<TransactionResponse> relatedTransactions;

    @JsonProperty("audit_trail")
    private List<AuditEventResponse> auditTrail;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSummaryDto {
        @JsonProperty("customer_id")
        private String customerId;
        private String email;
        @JsonProperty("account_age_days")
        private Integer accountAgeDays;
        @JsonProperty("total_transactions")
        private long totalTransactions;
        @JsonProperty("average_amount_inr")
        private double averageAmountInr;
        @JsonProperty("risk_segment")
        private String riskSegment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceSummaryDto {
        @JsonProperty("device_id")
        private String deviceId;
        @JsonProperty("total_transactions")
        private long totalTransactions;
        @JsonProperty("distinct_accounts")
        private long distinctAccounts;
        @JsonProperty("is_new_device")
        private boolean isNewDevice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpSummaryDto {
        @JsonProperty("ip_address")
        private String ipAddress;
        @JsonProperty("total_transactions")
        private long totalTransactions;
        @JsonProperty("distinct_accounts")
        private long distinctAccounts;
        @JsonProperty("is_new_ip")
        private boolean isNewIp;
    }
}
