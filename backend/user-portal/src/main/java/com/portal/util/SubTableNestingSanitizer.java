package com.portal.util;

import java.util.List;
import java.util.Map;

/**
 * Guards {@code variables.__subTables__} against geometric bloat.
 *
 * <p>The canonical storage model is {@code root -> slice -> row}, plus at most ONE level of
 * {@code parentRow.__subTables__[childBindingId]} (needed for Link Form / MI inline sub-tables, see
 * {@code portal-design-parity.mdc}). Submit/autosave/approve paths historically cloned binding rows that
 * still carried their previous-round nested {@code row.__subTables__} trees into the top-level slices, and
 * the backend persisted them as-is. Each task advance re-merged and re-embedded those trees, so depth and
 * slice count grew geometrically (observed: 21 top-level slices, ~18 real rows, but ~57k recursive slice
 * nodes and ~8s enrichment per request).
 *
 * <p>Top-level slices already hold all binding data, and read-side hydration reconstructs display from them,
 * so nested {@code __subTables__} below depth 1 is redundant and safe to drop before persist / before
 * enrichment. This keeps the legitimate one-level nesting and removes only the compounding deep copies.
 */
public final class SubTableNestingSanitizer {

    /** Allowed row-level nesting depth: depth-0 rows may keep one nested level; deeper copies are bloat. */
    private static final int MAX_ROW_NEST_DEPTH = 1;

    private SubTableNestingSanitizer() {
    }

    /**
     * Strips redundant deep nested {@code __subTables__} from the {@code __subTables__} payload inside the
     * given variables map, in place. Keeps one level of row nesting; removes nesting at depth >= 1.
     *
     * @return number of nested {@code __subTables__} maps removed (0 if nothing changed)
     */
    public static int stripDeepNestedSubTables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return 0;
        }
        Object subTables = variables.get("__subTables__");
        if (!(subTables instanceof Map<?, ?> subTablesMap) || subTablesMap.isEmpty()) {
            return 0;
        }
        return stripSlices(subTablesMap, 0);
    }

    @SuppressWarnings("unchecked")
    private static int stripSlices(Map<?, ?> subTables, int depth) {
        int removed = 0;
        for (Object sliceVal : subTables.values()) {
            if (!(sliceVal instanceof List<?> rows)) {
                continue;
            }
            for (Object rowObj : rows) {
                if (!(rowObj instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> row = (Map<String, Object>) rowObj;
                Object nested = row.get("__subTables__");
                if (!(nested instanceof Map<?, ?> nestedMap)) {
                    continue;
                }
                if (depth >= MAX_ROW_NEST_DEPTH) {
                    row.remove("__subTables__");
                    removed++;
                } else {
                    removed += stripSlices(nestedMap, depth + 1);
                }
            }
        }
        return removed;
    }
}
