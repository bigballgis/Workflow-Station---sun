package com.admin.ldap;

import java.time.Instant;

/**
 * LDAP 同步审计的对外视图 DTO（避免 Controller 直接返回 JPA 实体）。
 */
public record LdapSyncAuditView(
        String id,
        String syncType,
        String status,
        Integer totalFetched,
        Integer upserted,
        Integer failed,
        String message,
        Instant startedAt,
        Instant finishedAt) {

    public static LdapSyncAuditView from(LdapSyncAudit audit) {
        return new LdapSyncAuditView(
                audit.getId(),
                audit.getSyncType(),
                audit.getStatus(),
                audit.getTotalFetched(),
                audit.getUpserted(),
                audit.getFailed(),
                audit.getMessage(),
                audit.getStartedAt(),
                audit.getFinishedAt());
    }
}
