package com.portal.util;

import com.platform.common.jdbc.SubTableRowIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Walks {@code variables.__subTables__} and gives an identity to any row that arrived
 * without one, so a stored row can be addressed afterwards — by audit diffing, by the SQL
 * that expands sub-table rows for Main Table Views, and by anything that must tell two
 * rows apart.
 *
 * <p>Identity is assigned on canonical (numeric binding-id) slices first. Name / case
 * aliases of those slices must not receive a second UUID: JSON deserialization copies
 * each alias into its own Map, and a later Change History diff would treat those copies
 * as row add + delete.
 *
 * <p>Mutates the map in place, mirroring {@link SubTableNestingSanitizer}, and covers the
 * one legitimate level of {@code row.__subTables__} nesting that sanitizer preserves.
 */
public final class SubTableRowIdentityEnricher {

    private SubTableRowIdentityEnricher() {
    }

    /**
     * @return number of rows given a generated identity (0 when everything already had one)
     */
    public static int ensureRowIdentities(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return 0;
        }
        if (!(variables.get("__subTables__") instanceof Map<?, ?> subTables) || subTables.isEmpty()) {
            return 0;
        }
        return ensureInSlices(subTables);
    }

    @SuppressWarnings("unchecked")
    private static int ensureInSlices(Map<?, ?> subTables) {
        List<Map.Entry<?, ?>> numeric = new ArrayList<>();
        List<Map.Entry<?, ?>> aliases = new ArrayList<>();
        for (Map.Entry<?, ?> entry : subTables.entrySet()) {
            if (isNumericKey(entry.getKey())) {
                numeric.add(entry);
            } else {
                aliases.add(entry);
            }
        }
        int assigned = 0;
        for (Map.Entry<?, ?> entry : numeric) {
            assigned += ensureInSliceValue(entry.getValue());
        }
        if (numeric.isEmpty()) {
            for (Map.Entry<?, ?> entry : aliases) {
                assigned += ensureInSliceValue(entry.getValue());
            }
        }
        return assigned;
    }

    @SuppressWarnings("unchecked")
    private static int ensureInSliceValue(Object sliceValue) {
        if (!(sliceValue instanceof List<?> rows)) {
            return 0;
        }
        int assigned = 0;
        for (Object rowObject : rows) {
            if (!(rowObject instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) rowObject;
            if (SubTableRowIdentity.ensureIdentity(row)) {
                assigned++;
            }
            if (row.get("__subTables__") instanceof Map<?, ?> nested) {
                assigned += ensureInSlices(nested);
            }
        }
        return assigned;
    }

    private static boolean isNumericKey(Object key) {
        if (key == null) {
            return false;
        }
        String text = String.valueOf(key).trim();
        return !text.isEmpty() && text.chars().allMatch(Character::isDigit);
    }
}
