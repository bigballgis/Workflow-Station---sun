package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LDAP sync records columns ({@code ac_ldap_sync_audit}); fields match {@code LdapSyncAuditView}. Types and statuses
 * are the values the sync jobs write ({@code LdapUserSyncService}, {@code LdapSyncService}, {@code LdapConstants}).
 */
public final class LdapSyncAuditColumnSpec {

    private LdapSyncAuditColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.withOptions("syncType", "ldapSync.syncType", Kind.ENUM, List.of(
                        new ListColumnMeta.Option("FULL", "ldapSync.typeFull"),
                        new ListColumnMeta.Option("INCREMENTAL", "ldapSync.typeIncremental"),
                        new ListColumnMeta.Option("HERMES_AD_GROUP", "ldapSync.typeGroups"),
                        new ListColumnMeta.Option("HERMES_AD_INCR", "ldapSync.typeGroupsIncremental"))),
                ListColumnMeta.withOptions("status", "ldapSync.status", Kind.ENUM, List.of(
                        new ListColumnMeta.Option("RUNNING", "ldapSync.statusRunning"),
                        new ListColumnMeta.Option("SUCCESS", "ldapSync.statusSuccess"),
                        new ListColumnMeta.Option("PARTIAL", "ldapSync.statusPartial"),
                        new ListColumnMeta.Option("FAILED", "ldapSync.statusFailed"))),
                ListColumnMeta.of("startedAt", "ldapSync.startedAt", Kind.DATETIME),
                ListColumnMeta.of("durationMs", "ldapSync.duration", Kind.NUMBER),
                ListColumnMeta.of("totalFetched", "ldapSync.totalFetched", Kind.NUMBER),
                ListColumnMeta.of("insertCount", "ldapSync.insertCount", Kind.NUMBER),
                ListColumnMeta.of("updateCount", "ldapSync.updateCount", Kind.NUMBER),
                ListColumnMeta.of("failed", "ldapSync.failed", Kind.NUMBER),
                ListColumnMeta.of("skippedMissingKey", "ldapSync.skipped", Kind.NUMBER),
                ListColumnMeta.of("message", "ldapSync.message", Kind.TEXT));
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, LdapSyncAuditColumnSpec::sqlFor, "a.id", "a.started_at DESC");
    }

    /** NUMBER and DATETIME as text: {@link ListFilterSql} guards and casts text expressions. */
    static String sqlFor(String field) {
        return switch (field) {
            case "syncType" -> "a.sync_type";
            case "status" -> "a.status";
            case "startedAt" -> "a.started_at::text";
            case "durationMs" -> "a.duration_ms::text";
            case "totalFetched" -> "a.total_fetched::text";
            case "insertCount" -> "a.insert_count::text";
            case "updateCount" -> "a.update_count::text";
            case "failed" -> "a.failed::text";
            case "skippedMissingKey" -> "a.skipped_missing_key::text";
            case "message" -> "a.message";
            default -> throw new IllegalArgumentException("Unknown LDAP sync column: " + field);
        };
    }
}
