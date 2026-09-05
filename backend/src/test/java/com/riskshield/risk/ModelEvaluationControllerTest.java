package com.riskshield.risk;

import com.riskshield.risk.service.ModelEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ModelEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModelEvaluationService modelEvaluationService;

    @Test
    @DisplayName("GET /api/v1/model/evaluation/current returns all required held-out test set metrics")
    void testGetCurrentEvaluation() throws Exception {
        mockMvc.perform(get("/api/v1/model/evaluation/current")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation_label", is("Final evaluation on held-out test set")))
                .andExpect(jsonPath("$.model_version", is("v1.0.0-xgboost")))
                .andExpect(jsonPath("$.dataset_version", is("v1.0-synthetic-creditcard")))
                .andExpect(jsonPath("$.test_set_version", is("test-set-v1.0-heldout")))
                .andExpect(jsonPath("$.dataset_size", is(15000)))
                .andExpect(jsonPath("$.fraud_count", is(444)))
                .andExpect(jsonPath("$.non_fraud_count", is(14556)))
                .andExpect(jsonPath("$.precision", closeTo(0.99107, 0.0001)))
                .andExpect(jsonPath("$.recall", is(1.0)))
                .andExpect(jsonPath("$.f1", closeTo(0.99552, 0.0001)))
                .andExpect(jsonPath("$.roc_auc", closeTo(0.99997, 0.0001)))
                .andExpect(jsonPath("$.pr_auc", closeTo(0.99899, 0.0001)))
                .andExpect(jsonPath("$.false_positive_rate", closeTo(0.00027, 0.0001)))
                .andExpect(jsonPath("$.false_negative_rate", is(0.0)))
                .andExpect(jsonPath("$.confusion_matrix.true_negatives", is(14552)))
                .andExpect(jsonPath("$.confusion_matrix.false_positives", is(4)))
                .andExpect(jsonPath("$.confusion_matrix.false_negatives", is(0)))
                .andExpect(jsonPath("$.confusion_matrix.true_positives", is(444)))
                .andExpect(jsonPath("$.false_positive_cost", closeTo(776.48, 0.01)))
                .andExpect(jsonPath("$.false_negative_cost", is(0.0)))
                .andExpect(jsonPath("$.estimated_prevented_loss", closeTo(2508888.61, 0.01)));
    }

    @Test
    @DisplayName("GET /api/v1/model/evaluation/thresholds returns metrics for requested thresholds 0.50 to 0.90")
    void testGetThresholdEvaluations() throws Exception {
        mockMvc.perform(get("/api/v1/model/evaluation/thresholds")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                // Verify 0.50
                .andExpect(jsonPath("$[0].threshold", is(0.50)))
                .andExpect(jsonPath("$[0].precision", closeTo(0.99107, 0.0001)))
                .andExpect(jsonPath("$[0].recall", is(1.0)))
                .andExpect(jsonPath("$[0].confusion_matrix.false_positives", is(4)))
                .andExpect(jsonPath("$[0].false_positive_cost", closeTo(776.48, 0.01)))
                // Verify 0.70
                .andExpect(jsonPath("$[4].threshold", is(0.70)))
                .andExpect(jsonPath("$[4].recall", is(1.0)))
                // Verify 0.80
                .andExpect(jsonPath("$[6].threshold", is(0.80)))
                .andExpect(jsonPath("$[6].precision", closeTo(0.99329, 0.0001)))
                .andExpect(jsonPath("$[6].confusion_matrix.false_positives", is(3)))
                .andExpect(jsonPath("$[6].false_positive_cost", closeTo(604.64, 0.01)))
                // Verify 0.90
                .andExpect(jsonPath("$[8].threshold", is(0.90)))
                .andExpect(jsonPath("$[8].precision", closeTo(0.99552, 0.0001)))
                .andExpect(jsonPath("$[8].confusion_matrix.false_positives", is(2)))
                .andExpect(jsonPath("$[8].false_positive_cost", closeTo(426.29, 0.01)));
    }

    @Test
    @DisplayName("Data leakage guardrail throws exception if held-out test set is used in training")
    void testGuardrailThrowsOnTestLeakage() {
        assertThatThrownBy(() -> modelEvaluationService.validateNoTestLeakage("test_dataset_v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATA LEAKAGE VIOLATION");

        assertThatThrownBy(() -> modelEvaluationService.validateNoTestLeakage("heldout_split"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATA LEAKAGE VIOLATION");
    }
}
