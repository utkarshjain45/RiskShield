package com.riskshield.risk.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlRiskScoreRequest {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("ip_address")
    private String ipAddress;

    private Double amount;

    @JsonProperty("cust_hist_avg_amount")
    private Double custHistAvgAmount;

    @JsonProperty("amount_deviation")
    private Double amountDeviation;

    @JsonProperty("tx_count_5m")
    private Integer txCount5m;

    @JsonProperty("tx_count_30m")
    private Integer txCount30m;

    @JsonProperty("tx_count_1h")
    private Integer txCount1h;

    @JsonProperty("amount_spent_1h")
    private Double amountSpent1h;

    @JsonProperty("cust_tx_frequency")
    private Double custTxFrequency;

    @JsonProperty("cust_failed_rate")
    private Double custFailedRate;

    @JsonProperty("device_tx_count")
    private Integer deviceTxCount;

    @JsonProperty("device_account_count")
    private Integer deviceAccountCount;

    @JsonProperty("ip_tx_count")
    private Integer ipTxCount;

    @JsonProperty("ip_account_count")
    private Integer ipAccountCount;

    @JsonProperty("is_new_device")
    private Integer isNewDevice;

    @JsonProperty("is_new_ip")
    private Integer isNewIp;

    @JsonProperty("customer_account_age_days")
    private Integer customerAccountAgeDays;

    @JsonProperty("hour_of_day")
    private Integer hourOfDay;

    @JsonProperty("day_of_week")
    private Integer dayOfWeek;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("merchant_category")
    private String merchantCategory;

    @JsonProperty("merchant_risk_tier")
    private String merchantRiskTier;
}
