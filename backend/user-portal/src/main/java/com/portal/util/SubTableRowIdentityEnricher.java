package com.portal.util;

import com.platform.common.jdbc.SubTableRowIdentity;

import java.util.List;
import java.util.Map;

/**
 * Walks {@code variables.__subTables__} and gives an identity to any row that arrived
 * without one, so a stored row can be addressed afterwards — by audit diffing, by the SQL
 * that expands sub-table rows for Main Table Views, and by anything that must tell two
 * rows apart.
 *
 * <p>Rows are only identified today when their sub-table declares an auto primary key
 * ({@code ProcessSubTablePrimaryKeyEnricherComponent}); everything else — sub-tables with
 * no declared key, rows extracted from an inbound email, rows hydrated back from the
 * engine — can reach storage anonymous. Reading such a row leaves no honest option: two
 * anonymous rows cannot be distinguished without hashing their content, and content
 * hashing silently merges two genuinely different rows that happen to hold equal values.
 * So identity is established on the way in.
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
        int assigned = 0;
        for (Object sliceValue : subTables.values()) {
            if (!(sliceValue instanceof List<?> rows)) {
                continue;
            }
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
        }
        return assigned;
    }
}
