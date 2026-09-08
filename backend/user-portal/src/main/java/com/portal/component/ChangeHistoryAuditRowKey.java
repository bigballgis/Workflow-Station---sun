package com.portal.component;

import com.platform.common.jdbc.SubTableRowIdentity;
import com.platform.common.jdbc.SubTableRowKeySupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The key that decides whether two sub-table rows — in the before and after snapshots of the
 * same save — are the same logical row.
 *
 * <p>Preference order is deliberate. The designer's configured primary key
 * ({@code dw_field_definitions.is_primary_key}, e.g. {@code correspondence_id = Corr-000092})
 * is the only identity the platform itself allocates and never regenerates, so it wins over
 * {@code platformRowUuid}. Matching on the primary key means a re-issued platform key cannot
 * split one logical row into a phantom delete plus a duplicate add.
 *
 * <p>When the primary key is not yet allocated (first persist of a new row), the row still
 * matches by {@code platformRowUuid} so a save that adds the PK is an update of the same row, not
 * delete-plus-add.
 *
 * <p>The resolved key is stamped onto the projected audit row under {@link #FIELD} so every
 * downstream stage reads the same answer instead of re-deriving it. {@link #FIELD} is registered
 * as sub-table row metadata in {@link ChangeHistoryComponent}, which keeps it out of recorded
 * field names.
 */
final class ChangeHistoryAuditRowKey {

    /** Reserved key holding the resolved identity; never a business field. */
    static final String FIELD = "__auditRowKey";

    private ChangeHistoryAuditRowKey() {
    }

    /**
     * @param pkFields the binding's configured primary-key columns, empty when the designer
     *                 configured none
     * @return the identity of {@code row}, or {@code null} when the row carries none and
     *         therefore cannot be tracked across snapshots
     */
    static String derive(Map<?, ?> row, List<String> pkFields) {
        if (row == null) {
            return null;
        }
        Map<String, Object> normalized = SubTableRowKeySupport.normalizeStringKeyMap(row);
        String configured = fromPrimaryKey(normalized, pkFields);
        return configured != null ? configured : rowIdValue(normalized);
    }

    /** Reads the key stamped by the projection, falling back to a plain identity lookup. */
    static String resolve(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object stamped = row.get(FIELD);
        if (stamped != null) {
            String text = String.valueOf(stamped).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return derive(row, List.of());
    }

    static void stamp(Map<String, Object> row, List<String> pkFields) {
        if (row == null) {
            return;
        }
        String derived = derive(row, pkFields);
        if (derived == null) {
            return;
        }
        Object existing = row.get(FIELD);
        if (existing != null) {
            String text = String.valueOf(existing).trim();
            if (!text.isEmpty() && (pkFields == null || pkFields.isEmpty())) {
                return;
            }
        }
        row.put(FIELD, derived);
    }

    /**
     * Same logical row when the configured primary keys match, or when any persist-time
     * identity value (platform key plus configured PK columns) matches. Never compares
     * business field values.
     */
    static boolean sameLogicalRow(
            Map<String, Object> left, Map<String, Object> right, List<String> pkFields) {
        if (left == null || right == null) {
            return false;
        }
        String pkLeft = fromPrimaryKey(left, pkFields);
        String pkRight = fromPrimaryKey(right, pkFields);
        if (pkLeft != null && pkLeft.equals(pkRight)) {
            return true;
        }
        Set<String> leftIds = SubTableRowIdentity.identityValuesOf(left, pkFields);
        Set<String> rightIds = SubTableRowIdentity.identityValuesOf(right, pkFields);
        if (leftIds.isEmpty() || rightIds.isEmpty()) {
            return false;
        }
        for (String id : leftIds) {
            if (rightIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    static String fromPrimaryKey(Map<String, Object> row, List<String> pkFields) {
        if (row == null || pkFields == null || pkFields.isEmpty()) {
            return null;
        }
        List<String> filledNames = new ArrayList<>();
        List<String> filledValues = new ArrayList<>();
        for (String field : pkFields) {
            Object value = SubTableRowKeySupport.getRowValueIgnoreCase(row, field);
            String text = value == null ? "" : String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            filledNames.add(field);
            filledValues.add(text);
        }
        if (filledValues.isEmpty()) {
            return null;
        }
        if (filledValues.size() == 1) {
            return filledValues.get(0);
        }
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < filledValues.size(); i++) {
            if (i > 0) {
                key.append('|');
            }
            key.append(filledNames.get(i)).append('=').append(filledValues.get(i));
        }
        return key.toString();
    }

    static String rowIdValue(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        String field = SubTableRowIdentity.identityFieldOf(row);
        if (field == null) {
            return null;
        }
        Object value = SubTableRowKeySupport.getRowValueIgnoreCase(row, field);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
