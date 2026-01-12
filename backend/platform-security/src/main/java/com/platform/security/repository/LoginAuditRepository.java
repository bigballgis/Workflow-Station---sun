package com.platform.security.repository;

import com.platform.security.model.LoginAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for LoginAudit entity operations.
 * Validates: Requirements 2.5, 3.4
 */
@Repository
public interface LoginAuditRepository extends JpaRepository<LoginAudit, UUID> {

    /**
     * Find audit records by user ID.
     */
    List<LoginAudit> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find audit records by username.
     */
    List<LoginAudit> findByUsernameOrderByCreatedAtDesc(String username);

    /**
     * Find audit records by action type.
     */
    List<LoginAudit> findByActionOrderByCreatedAtDesc(LoginAudit.AuditAction action);

    /**
     * Find audit records within a time range.
     */
    List<LoginAudit> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start, LocalDateTime end);

    /**
     * Find failed login attempts for a username.
     */
    List<LoginAudit> findByUsernameAndSuccessFalseOrderByCreatedAtDesc(String username);

    /**
     * Count failed login attempts for a username within a time range.
     */
    long countByUsernameAndSuccessFalseAndCreatedAtAfter(String username, LocalDateTime after);

    /**
     * Find audit records with pagination.
     */
    Page<LoginAudit> findByUsernameContainingIgnoreCaseOrderByCreatedAtDesc(
            String username, Pageable pageable);

    /**
     * Find recent audit records.
     */
    List<LoginAudit> findTop100ByOrderByCreatedAtDesc();
}
