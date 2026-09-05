package com.riskshield.spike.service;

import com.riskshield.audit.service.AuditService;
import com.riskshield.common.exception.ResourceNotFoundException;
import com.riskshield.spike.dto.FraudIncidentDto;
import com.riskshield.spike.entity.FraudIncident;
import com.riskshield.spike.entity.IncidentStatus;
import com.riskshield.spike.repository.FraudIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentManagementService {

    private final FraudIncidentRepository fraudIncidentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<FraudIncidentDto> getIncidents(String merchantId, IncidentStatus status, Pageable pageable) {
        String effectiveMerchant = com.riskshield.security.util.SecurityUtils.resolveMerchantScope(merchantId);
        Page<FraudIncident> page;

        if (effectiveMerchant != null && !effectiveMerchant.isBlank() && status != null) {
            page = fraudIncidentRepository.findByMerchant_IdAndStatusOrderByDetectedAtDesc(effectiveMerchant, status, pageable);
        } else if (effectiveMerchant != null && !effectiveMerchant.isBlank()) {
            page = fraudIncidentRepository.findByMerchant_IdOrderByDetectedAtDesc(effectiveMerchant, pageable);
        } else if (status != null) {
            page = fraudIncidentRepository.findByStatusOrderByDetectedAtDesc(status, pageable);
        } else {
            page = fraudIncidentRepository.findAllByOrderByDetectedAtDesc(pageable);
        }

        return page.map(FraudIncidentDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public FraudIncidentDto getIncidentById(String incidentId) {
        FraudIncident incident = fraudIncidentRepository.findByIdWithMerchant(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("FraudIncident not found with ID: " + incidentId));

        if (incident.getMerchant() != null) {
            com.riskshield.security.util.SecurityUtils.assertMerchantAccess(incident.getMerchant().getId());
        }

        return FraudIncidentDto.fromEntity(incident);
    }

    @Transactional
    public FraudIncidentDto acknowledgeIncident(String incidentId, String actorId) {
        FraudIncident incident = fraudIncidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("FraudIncident not found with ID: " + incidentId));

        if (incident.getMerchant() != null) {
            com.riskshield.security.util.SecurityUtils.assertMerchantAccess(incident.getMerchant().getId());
        }

        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            throw new IllegalStateException("Cannot acknowledge an already RESOLVED incident: " + incidentId);
        }

        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incident.setAcknowledgedAt(Instant.now());
        FraudIncident saved = fraudIncidentRepository.save(incident);

        auditService.recordRiskEvent(
                com.riskshield.audit.entity.AuditEventType.INCIDENT_ACKNOWLEDGED,
                com.riskshield.audit.entity.ActorType.USER,
                actorId != null ? actorId : "SECURITY_OPERATIONS",
                incident.getMerchant() != null ? incident.getMerchant().getId() : null,
                null,
                "FraudIncident",
                incidentId,
                "spike-management-service",
                "Incident acknowledged by investigator " + actorId,
                saved
        );

        log.info("Incident {} acknowledged by {}", incidentId, actorId);
        return FraudIncidentDto.fromEntity(saved);
    }

    @Transactional
    public FraudIncidentDto resolveIncident(String incidentId, String actorId, String notes) {
        FraudIncident incident = fraudIncidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("FraudIncident not found with ID: " + incidentId));

        if (incident.getMerchant() != null) {
            com.riskshield.security.util.SecurityUtils.assertMerchantAccess(incident.getMerchant().getId());
        }

        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now());
        if (notes != null && !notes.isBlank()) {
            String updatedSummary = (incident.getExplanationSummary() != null ? incident.getExplanationSummary() : "")
                    + " [Resolution Notes: " + notes + "]";
            incident.setExplanationSummary(updatedSummary);
        }
        FraudIncident saved = fraudIncidentRepository.save(incident);

        auditService.recordRiskEvent(
                com.riskshield.audit.entity.AuditEventType.INCIDENT_RESOLVED,
                com.riskshield.audit.entity.ActorType.USER,
                actorId != null ? actorId : "SECURITY_OPERATIONS",
                incident.getMerchant() != null ? incident.getMerchant().getId() : null,
                null,
                "FraudIncident",
                incidentId,
                "spike-management-service",
                "Incident resolved by " + actorId + ": " + (notes != null ? notes : "Mitigation completed"),
                saved
        );

        log.info("Incident {} resolved by {}", incidentId, actorId);
        return FraudIncidentDto.fromEntity(saved);
    }
}
