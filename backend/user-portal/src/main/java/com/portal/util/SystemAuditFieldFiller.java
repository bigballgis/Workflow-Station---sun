package com.portal.util;

import com.platform.common.audit.SystemAuditFields;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Fills Table Design system audit fields ({@code created_at} / {@code created_by} /
 * {@code updated_at} / {@code updated_by}) into main-form process variables at real
 * persistence time.
 *
 * <p>Rules (platform-managed; independent of Form Design canvas):</p>
 * <ul>
 *   <li>Every DW table auto-appends these four columns ({@code TableDesignComponentImpl}).
 *       Values are written into process variables whenever a real insert/update runs —
 *       designers do <b>not</b> need to place the fields on the form (Form Design strips
 *       them from the canvas by design).</li>
 *   <li>{@link #fillOnInsert} runs at process start: fills all four fields (overwrites any
 *       client-supplied values).</li>
 *   <li>{@link #fillOnUpdate} runs at task-form / process-form submits: refreshes only
 *       {@code updated_at} / {@code updated_by}; {@code created_*} is preserved.</li>
 * </ul>
 *
 * <p>字段名判定唯一来源 = {@link SystemAuditFields}（platform-common）。
 * Values mirror the portal sub-table convention ({@code rowInit.ts}): timestamps are
 * {@code yyyy-MM-dd HH:mm:ss} strings in UTC+8, user fields carry the display name.</p>
 */
public final class SystemAuditFieldFiller {

    public static final String CREATED_AT = SystemAuditFields.CREATED_AT;
    public static final String CREATED_BY = SystemAuditFields.CREATED_BY;
    public static final String UPDATED_AT = SystemAuditFields.UPDATED_AT;
    public static final String UPDATED_BY = SystemAuditFields.UPDATED_BY;

    private static final ZoneId AUDIT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter AUDIT_TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SystemAuditFieldFiller() {
    }

    /** Fill created_* and updated_* on the real insert (process start). */
    public static void fillOnInsert(Map<String, Object> variables, String userDisplayName) {
        if (variables == null) {
            return;
        }
        String now = now();
        put(variables, CREATED_AT, now);
        put(variables, CREATED_BY, userDisplayName);
        put(variables, UPDATED_AT, now);
        put(variables, UPDATED_BY, userDisplayName);
    }

    /** Refresh updated_* on a real update (task form / returned-form submit); created_* untouched. */
    public static void fillOnUpdate(Map<String, Object> variables, String userDisplayName) {
        if (variables == null) {
            return;
        }
        put(variables, UPDATED_AT, now());
        put(variables, UPDATED_BY, userDisplayName);
    }

    /**
     * Drop client-supplied audit keys before merging a form payload into process variables.
     * Prevents Parameter Tampering from overwriting platform-owned {@code created_*} /
     * {@code updated_*} prior to {@link #fillOnUpdate}.
     */
    public static void stripClientAuditKeys(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        payload.keySet().removeIf(SystemAuditFields::isAuditField);
    }

    private static void put(Map<String, Object> variables, String key, String value) {
        if (value != null && !value.isBlank()) {
            variables.put(key, value);
        }
    }

    private static String now() {
        return AUDIT_TS_FORMAT.format(ZonedDateTime.now(AUDIT_ZONE));
    }
}
