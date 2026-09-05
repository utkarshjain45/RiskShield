package com.riskshield.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riskshield.demo.entity.SimulationMode;
import com.riskshield.demo.entity.SimulationStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationStatusDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("mode")
    private SimulationMode mode;

    @JsonProperty("mode_display_name")
    private String modeDisplayName;

    @JsonProperty("mode_description")
    private String modeDescription;

    @JsonProperty("mode_expected_outcome")
    private String modeExpectedOutcome;

    @JsonProperty("status")
    private SimulationStatus status;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("target_count")
    private Integer targetCount;

    @JsonProperty("generated_count")
    private Integer generatedCount;

    @JsonProperty("allowed_count")
    private Integer allowedCount;

    @JsonProperty("review_count")
    private Integer reviewCount;

    @JsonProperty("blocked_count")
    private Integer blockedCount;

    @JsonProperty("fraud_rate")
    private Double fraudRate;

    @JsonProperty("interval_ms")
    private Integer intervalMs;

    @JsonProperty("active_incident_id")
    private String activeIncidentId;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("stopped_at")
    private Instant stoppedAt;
}
