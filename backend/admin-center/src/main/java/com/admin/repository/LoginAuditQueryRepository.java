package com.admin.repository;

import com.platform.security.entity.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for querying login audit records from sys_login_audit.
 * Separate from platform-security's LoginAuditRepository to avoid bean conflicts.
 */
@Repository
public interface LoginAuditQueryRepository extends JpaRepository<LoginAudit, UUID> {

    /**
     * Find audit records by user ID, most recent first.
     */
    List<LoginAudit> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find audit records by username, most recent first.
     */
    List<LoginAudit> findByUsernameOrderByCreatedAtDesc(String username);

    /**
     * Find audit records by user ID within a date window, most recent first.
     */
    @Query("SELECT a FROM LoginAudit a WHERE a.userId = :userId AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<LoginAudit> findByUserIdSince(@Param("userId") String userId, @Param("since") LocalDateTime since);

    /**
     * Delete audit records older than the given cutoff.
     */
    @Modifying
    @Query("DELETE FROM LoginAudit a WHERE a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
