package com.admin.repository;

import com.platform.security.entity.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
