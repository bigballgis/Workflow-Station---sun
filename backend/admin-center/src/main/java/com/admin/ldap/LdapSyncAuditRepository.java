package com.admin.ldap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LDAP 同步审计仓库。
 */
@Repository
public interface LdapSyncAuditRepository extends JpaRepository<LdapSyncAudit, String> {

    /** 最近一次成功的同步（任意类型），用于增量水位回退到全量基线判断。 */
    Optional<LdapSyncAudit> findTopByStatusOrderByStartedAtDesc(String status);

    /** 最近一次指定类型 + 状态的同步记录。 */
    Optional<LdapSyncAudit> findTopBySyncTypeAndStatusOrderByStartedAtDesc(String syncType, String status);

    /** 同步历史（分页，时间倒序）。 */
    Page<LdapSyncAudit> findAllByOrderByStartedAtDesc(Pageable pageable);

    /** 同步历史（前 N 条，时间倒序）。 */
    List<LdapSyncAudit> findTop20ByOrderByStartedAtDesc();
}
