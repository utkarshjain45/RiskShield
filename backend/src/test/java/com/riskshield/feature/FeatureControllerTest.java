package com.riskshield.feature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.common.exception.ResourceNotFoundException;
import com.riskshield.common.filter.CorrelationIdFilter;
import com.riskshield.feature.dto.CustomerVelocityDto;
import com.riskshield.feature.dto.DeviceVelocityDto;
import com.riskshield.feature.dto.FeatureSnapshot;
import com.riskshield.feature.dto.IpVelocityDto;
import com.riskshield.feature.service.BehavioralFeatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class FeatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BehavioralFeatureService behavioralFeatureService;

    @Test
    @DisplayName("GET /api/v1/features/transaction/{id} should return feature snapshot")
    void testGetFeatureSnapshotSuccess() throws Exception {
        FeatureSnapshot snapshot = FeatureSnapshot.builder()
                .transactionId("tx_snap_001")
                .customerVelocity(CustomerVelocityDto.builder()
                        .transactions5m(4)
                        .transactions30m(8)
                        .transactions1h(15)
                        .amount1h(250000L)
                        .build())
                .deviceVelocity(DeviceVelocityDto.builder()
                        .transactions5m(2)
                        .transactions1h(5)
                        .accountCount1h(3)
                        .build())
                .ipVelocity(IpVelocityDto.builder()
                        .transactions5m(2)
                        .transactions1h(6)
                        .accountCount1h(4)
                        .build())
                .amountVelocity(250000L)
                .deviceAccountCount(3L)
                .ipAccountCount(4L)
                .isNewDevice(true)
                .isNewIp(false)
                .capturedAt(Instant.now())
                .build();

        when(behavioralFeatureService.getSnapshotByTransactionId(eq("tx_snap_001"))).thenReturn(snapshot);

        mockMvc.perform(get("/api/v1/features/transaction/tx_snap_001")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-feat-test-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.transaction_id", is("tx_snap_001")))
                .andExpect(jsonPath("$.data.customer_velocity.transactions_5m", is(4)))
                .andExpect(jsonPath("$.data.customer_velocity.transactions_30m", is(8)))
                .andExpect(jsonPath("$.data.customer_velocity.transactions_1h", is(15)))
                .andExpect(jsonPath("$.data.device_account_count", is(3)))
                .andExpect(jsonPath("$.data.ip_account_count", is(4)))
                .andExpect(jsonPath("$.data.is_new_device", is(true)))
                .andExpect(jsonPath("$.data.is_new_ip", is(false)));
    }

    @Test
    @DisplayName("GET /api/v1/features/transaction/{id} should return 404 when transaction is not found")
    void testGetFeatureSnapshotNotFound() throws Exception {
        when(behavioralFeatureService.getSnapshotByTransactionId(eq("tx_nonexistent")))
                .thenThrow(new ResourceNotFoundException("Transaction not found: tx_nonexistent"));

        mockMvc.perform(get("/api/v1/features/transaction/tx_nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
