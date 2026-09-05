package com.riskshield.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionTimelineDto {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("event_count")
    private int eventCount;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("last_event_at")
    private Instant lastEventAt;

    private List<AuditEventResponse> events;
}
