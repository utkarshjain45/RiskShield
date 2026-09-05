package com.riskshield.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryDto {

    @JsonProperty("id")
    private String id;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
