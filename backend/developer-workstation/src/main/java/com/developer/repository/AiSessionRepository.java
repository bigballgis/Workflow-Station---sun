package com.developer.repository;

import com.developer.entity.AiSession;
import com.developer.enums.AiSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AI 会话仓库
 */
@Repository
public interface AiSessionRepository extends JpaRepository<AiSession, Long> {

    Optional<AiSession> findByFunctionUnitIdAndUserIdAndStatus(Long functionUnitId, String userId, AiSessionStatus status);

    List<AiSession> findByFunctionUnitIdOrderByCreatedAtDesc(Long functionUnitId);

    Optional<AiSession> findBySessionId(UUID sessionId);
}
