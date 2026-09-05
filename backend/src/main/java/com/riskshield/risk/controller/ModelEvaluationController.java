package com.riskshield.risk.controller;

import com.riskshield.risk.dto.CurrentEvaluationResponse;
import com.riskshield.risk.dto.ThresholdEvaluationDto;
import com.riskshield.risk.service.ModelEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/model/evaluation")
@RequiredArgsConstructor
@Tag(name = "Model Evaluation", description = "Performance and financial impact evaluation on held-out test set")
public class ModelEvaluationController {

    private final ModelEvaluationService modelEvaluationService;

    @GetMapping("/current")
    @Operation(summary = "Get current benchmark evaluation on held-out test set")
    public ResponseEntity<CurrentEvaluationResponse> getCurrentEvaluation() {
        CurrentEvaluationResponse response = modelEvaluationService.getCurrentEvaluation();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/thresholds")
    @Operation(summary = "Get precision, recall, confusion matrix and financial costs across decision thresholds")
    public ResponseEntity<List<ThresholdEvaluationDto>> getThresholdEvaluations() {
        List<ThresholdEvaluationDto> thresholds = modelEvaluationService.getThresholdEvaluations();
        return ResponseEntity.ok(thresholds);
    }
}
