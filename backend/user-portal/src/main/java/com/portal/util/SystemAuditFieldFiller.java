package com.portal.util;

import com.platform.common.audit.SystemAuditFields;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Fills Table Design system audit fields (created_at / created_by / updated_at / updated_by)
 * into main-form process variables at real persistence time.
 *
 * <p>Rules (issue: audit values must be generated on insert/update, never when a dialog opens):</p>
 * <ul>
 *   <li>Only keys already present in the variables map are filled — the portal submits a key for
 *       every field placed on the Form Design form, so key presence == "field is on the form".
 *       Tables whose audit fields were not dragged onto the form get no values.</li>
 *   <li>{@link #fillOnInsert} runs at process start (the real insert): fills all four fields.</li>
 *   <li>{@link #fillOnUpdate} runs at task-form / process-form submits (real updates): refreshes
 *       only updated_at / updated_by; created_* is preserved.</li>
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
        putIfKeyPresent(variables, CREATED_AT, now);
        putIfKeyPresent(variables, CREATED_BY, userDisplayName);
        putIfKeyPresent(variables, UPDATED_AT, now);
        putIfKeyPresent(variables, UPDATED_BY, userDisplayName);
    }

    /** Refresh updated_* on a real update (task form / returned-form submit); created_* untouched. */
    public static void fillOnUpdate(Map<String, Object> variables, String userDisplayName) {
        if (variables == null) {
            return;
        }
        putIfKeyPresent(variables, UPDATED_AT, now());
        putIfKeyPresent(variables, UPDATED_BY, userDisplayName);
    }

    private static void putIfKeyPresent(Map<String, Object> variables, String key, String value) {
        if (variables.containsKey(key) && value != null && !value.isBlank()) {
            variables.put(key, value);
        }
    }

    private static String now() {
        return AUDIT_TS_FORMAT.format(ZonedDateTime.now(AUDIT_ZONE));
    }
}
