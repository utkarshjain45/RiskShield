package com.riskshield.assistant.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionResult {

    private String toolName;
    private Map<String, Object> arguments;
    private boolean success;
    private Object data;
    private String sourceEntity;
    private String errorMessage;
}
