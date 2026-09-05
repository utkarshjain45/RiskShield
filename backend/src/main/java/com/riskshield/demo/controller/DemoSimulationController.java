package com.riskshield.demo.controller;

import com.riskshield.common.dto.ApiResponse;
import com.riskshield.demo.dto.SimulationModeDto;
import com.riskshield.demo.dto.SimulationRequest;
import com.riskshield.demo.dto.SimulationStatusDto;
import com.riskshield.demo.service.DemoSimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/demo/simulations")
@RequiredArgsConstructor
public class DemoSimulationController {

    private final DemoSimulationService simulationService;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<SimulationStatusDto>> startSimulation(
            @Valid @RequestBody SimulationRequest request
    ) {
        log.info("REST: Starting demo simulation mode: {}", request.getMode());
        SimulationStatusDto dto = simulationService.startSimulation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Demo simulation launched successfully", dto));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<ApiResponse<SimulationStatusDto>> stopSimulation(
            @PathVariable("id") String id
    ) {
        log.info("REST: Stopping demo simulation: {}", id);
        SimulationStatusDto dto = simulationService.stopSimulation(id);
        return ResponseEntity.ok(ApiResponse.ok("Demo simulation stopped", dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SimulationStatusDto>> getSimulation(
            @PathVariable("id") String id
    ) {
        SimulationStatusDto dto = simulationService.getSimulation(id);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<SimulationStatusDto>> getActiveSimulation() {
        return simulationService.getActiveSimulation()
                .map(dto -> ResponseEntity.ok(ApiResponse.ok(dto)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok("No active simulation", null)));
    }

    @GetMapping("/modes")
    public ResponseEntity<ApiResponse<List<SimulationModeDto>>> getModes() {
        List<SimulationModeDto> modes = simulationService.getAvailableModes();
        return ResponseEntity.ok(ApiResponse.ok(modes));
    }
}
