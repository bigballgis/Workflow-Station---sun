package com.developer.util;

import java.util.Locale;
import java.util.Set;

/**
 * Standard audit fields auto-appended to every table by {@code TableDesignComponentImpl}.
 * They must not appear in Form Design canvas / list view auto-fill.
 */
public final class TableAuditFields {

    private static final Set<String> NAMES = Set.of(
            "created_at", "created_by", "updated_at", "updated_by");

    private TableAuditFields() {
    }

    public static boolean isAuditField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        return NAMES.contains(fieldName.trim().toLowerCase(Locale.ROOT));
    }
}
