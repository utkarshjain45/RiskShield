package com.riskshield.policy.dto;

import com.riskshield.common.enums.RiskDecisionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRuleDto {
    private String id;

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    @Builder.Default
    private String ruleType = "THRESHOLD";

    @NotBlank(message = "Condition operator is required (e.g. GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL)")
    private String conditionOperator;

    @NotBlank(message = "Condition value is required")
    private String conditionValue;

    @NotNull(message = "Rule action is required (ALLOW, REVIEW, BLOCK)")
    private RiskDecisionType action;

    @Builder.Default
    private Integer priority = 100;

    @Builder.Default
    private boolean enabled = true;
}
