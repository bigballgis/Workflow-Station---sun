package com.platform.common.computedfield;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collapses a raw {@code __subTables__} map into exactly one slice per table, keyed by table name.
 *
 * <p>WHY THIS EXISTS: process variables carry {@code __subTables__} under several keys that all
 * point at copies of the same rows — the bindingId, the exact table name, and the human display
 * name. An aggregate that visits every matching key double- or triple-counts money while looking
 * entirely plausible, which is the worst possible failure mode for an approval amount.
 *
 * <p>Slices resolving to a tableId that has already been seen are DROPPED, not merged. Merging is
 * the bug. Keying the output by table name rather than by whichever alias came first also matters:
 * an aggregate node references {@code request_items}, so a slice that arrived only under the
 * bindingId key {@code "42"} must still be findable under {@code request_items}.
 *
 * @see <a href="file:../../../../../../../../frontend/shared/src/computedField/evaluator.ts">evaluator.ts normalizeSubTables</a>
 */
public final class SubTableNormalizer {

    /**
     * What a {@code __subTables__} slice key refers to.
     *
     * @param tableId   table id, or null when unknown
     * @param tableName real table name, which becomes the canonical key
     */
    public record SliceIdentity(Object tableId, String tableName) {
    }

    /** Resolves a slice key to the table it belongs to. */
    @FunctionalInterface
    public interface SliceResolver {

        /**
         * Identifies a slice.
         *
         * @param sliceKey key as it appears in {@code __subTables__}
         * @return the identity, or null when the key cannot be resolved
         */
        SliceIdentity identify(String sliceKey);
    }

    private SubTableNormalizer() {
    }

    /**
     * Normalizes the raw slice map.
     *
     * @param raw      the {@code __subTables__} value, may be null
     * @param resolver resolves slice keys to tables; null means "names only", which is what the
     *                 design-time preview has available
     * @return canonical lower-cased table name to rows, one entry per real table
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<Map<String, Object>>> normalize(Map<String, Object> raw,
                                                                  SliceResolver resolver) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!(entry.getValue() instanceof List<?> rawRows)) {
                continue;
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object row : rawRows) {
                if (row instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
            String sliceKey = entry.getKey();
            SliceIdentity identity = resolver != null ? resolver.identify(sliceKey) : null;
            String tableName = identity != null && identity.tableName() != null
                    ? identity.tableName().trim() : null;
            String canonical = (tableName != null && !tableName.isEmpty() ? tableName : sliceKey)
                    .toLowerCase();
            boolean hasId = identity != null && identity.tableId() != null
                    && !String.valueOf(identity.tableId()).isEmpty();
            String dedupKey = hasId ? "id:" + identity.tableId() : "name:" + canonical;

            if (!seen.add(dedupKey)) {
                continue;
            }
            result.put(canonical, rows);
        }
        return result;
    }
}
