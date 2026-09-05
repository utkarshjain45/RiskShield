package com.riskshield.assistant.repository;

import com.riskshield.assistant.entity.AiInvestigationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiInvestigationSessionRepository extends JpaRepository<AiInvestigationSession, String> {

    List<AiInvestigationSession> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<AiInvestigationSession> findByMerchantIdOrderByUpdatedAtDesc(String merchantId);

    List<AiInvestigationSession> findAllByOrderByUpdatedAtDesc();
}
