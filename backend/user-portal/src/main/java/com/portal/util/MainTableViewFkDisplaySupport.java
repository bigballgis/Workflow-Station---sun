package com.portal.util;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolve Main Table View {@code fk_display} cells by matching a row's FK scalar against
 * the same process instance's MAIN variables using {@code ref_primary_key_fields}.
 */
public final class MainTableViewFkDisplaySupport {

    private MainTableViewFkDisplaySupport() {}

    /**
     * @param mainVars         same process-instance top-level variables (MAIN table fields)
     * @param fkValue          FK scalar from the current row (e.g. Attachment.case_id)
     * @param primaryKeyFields referenced table PK field names (e.g. {@code ["case_number"]})
     * @param displayField     attribute to read from MAIN vars when FK matches
     * @return matched attribute, or {@code null} when unmatched (caller shows raw FK)
     */
    public static Object resolveAttribute(
            Map<String, Object> mainVars,
            Object fkValue,
            List<String> primaryKeyFields,
            String displayField) {
        if (mainVars == null || mainVars.isEmpty() || fkValue == null
                || displayField == null || displayField.isBlank()) {
            return null;
        }
        String fkScalar = scalarString(fkValue);
        if (fkScalar == null) {
            return null;
        }
        if (primaryKeyFields != null) {
            for (String pkField : primaryKeyFields) {
                if (pkField == null || pkField.isBlank()) {
                    continue;
                }
                if (fkEquals(mainVars.get(pkField), fkScalar)) {
                    return mainVars.get(displayField);
                }
            }
        }
        // Match ONLY on the referenced table's configured primary key (ref_primary_key_fields).
        // A previous fallback tried the literals "id" / "id_idw" when PK metadata was missing: tables
        // whose PK happens to use those names looked fine, while a table with a differently named PK
        // that also carries an "id" column matched the WRONG row and displayed a wrong related
        // attribute. Guessing a column name is worse than not resolving: return null so the caller
        // shows the raw FK value.
        return null;
    }

    static boolean fkEquals(Object mainPkValue, String fkScalar) {
        String main = scalarString(mainPkValue);
        return main != null && Objects.equals(main, fkScalar);
    }

    static String scalarString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }
}
