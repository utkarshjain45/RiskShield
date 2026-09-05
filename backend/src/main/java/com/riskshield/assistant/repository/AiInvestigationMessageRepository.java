package com.riskshield.assistant.repository;

import com.riskshield.assistant.entity.AiInvestigationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiInvestigationMessageRepository extends JpaRepository<AiInvestigationMessage, Long> {

    @Query("SELECT m FROM AiInvestigationMessage m WHERE m.session.id = :sessionId ORDER BY m.createdAt ASC")
    List<AiInvestigationMessage> findBySessionIdOrderByCreatedAtAsc(@Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM AiInvestigationMessage m WHERE m.session.id = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);
}
