package com.portal.component;

import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MI (multi-instance) sub-task row-level save isolation.
 *
 * <p>An MI sub-task's Task Detail page only ever hydrates a "thin" view of sibling participants'
 * rows (own row full, others reduced to identity fields — see {@code useTaskDetailMiIsolation.ts}
 * on the frontend). Naively replacing a shared {@code __subTables__} array with that submitted
 * view — as plain {@code Map.putAll} does — silently overwrites every other participant's
 * previously-saved data with those thin stubs. This class instead merges ONLY the current MI
 * sub-task's own row (identified by the BPMN {@code _currentItem}/{@code currentItem} loop
 * variable, already present in every MI sub-task's submitted form data) into the row that already
 * exists in the database, leaving every other row exactly as persisted.
 *
 * <p><b>Technical debt</b>: MI detection relies on {@code _currentItem}/{@code currentItem} being
 * present in the submitted {@code formData} — an implicit consequence of the frontend's
 * {@code buildCurrentTaskFormSubmitPayload} spreading the whole in-memory form state, not an
 * explicit contract. If a future frontend refactor stops forwarding that key, MI detection here
 * silently reverts to treating the submission as non-MI (whole-array replace) rather than failing
 * loud — same behavior as any other non-MI task, so not a data-loss regression by itself, but the
 * row-isolation guarantee this class exists to provide would quietly stop applying.
 */
@Component
@RequiredArgsConstructor
class MiSubTaskSubTableRowMerger {

    private final JdbcTemplate jdbcTemplate;

    /** Canonical MI collection row PK column, tried before the legacy {@code id} alias (id/id_idw aliasing is handled inside {@link SubTableRowKeySupport}). */
    private static final List<String> DEFAULT_PK_COLUMNS = List.of("id_idw");

    /**
     * Foreign-key column names that mark a sub-table as scoped to the WHOLE process/request rather
     * than to one MI participant row — e.g. a shared attachment table FK'd by {@code main_id} to the
     * request itself. Mirrors the frontend's {@code SHARED_PROCESS_SUB_TABLE_FK}
     * (subTableBindingKinds.ts) so both sides agree on which bindings must NOT be row-scoped by the
     * current sub-task's participant PK.
     */
    private static final Set<String> SHARED_PROCESS_SUB_TABLE_FK = Set.of(
            "main_id", "mainid", "process_id", "processid", "main_record_id");

    /**
     * Whether {@code formData} identifies this submission as an MI sub-task (carries a
     * {@code _currentItem}/{@code currentItem} BPMN loop variable) — regardless of whether that
     * variable's row key is actually resolvable. Callers use this to decide whether a failed
     * {@link #resolveCurrentItemRowKey} result means "not MI, skip row isolation" (this returns
     * {@code false}) or "MI, but broken — must not save" (this returns {@code true}).
     */
    boolean isMiSubTaskSubmission(Map<String, Object> formData) {
        if (formData == null) {
            return false;
        }
        return formData.get("_currentItem") instanceof Map || formData.get("currentItem") instanceof Map;
    }

    /**
     * @return the current MI sub-task's own row key columns/values, or {@code null} when the row
     *         key cannot be resolved from {@code _currentItem}/{@code currentItem} (missing PK
     *         value, unexpected shape, …). Only call when {@link #isMiSubTaskSubmission} is true —
     *         a {@code null} result here means "MI, but broken", not "not MI".
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> resolveCurrentItemRowKey(Map<String, Object> formData) {
        Object raw = formData.get("_currentItem");
        if (raw == null) {
            raw = formData.get("currentItem");
        }
        if (!(raw instanceof Map)) {
            return null;
        }
        return SubTableRowKeySupport.rowKeyFromCurrentItem((Map<String, Object>) raw, DEFAULT_PK_COLUMNS);
    }

    /**
     * Fails the save outright when a confirmed MI sub-task submission's own row key could not be
     * resolved — never silently falls back to a whole-array replace, which would risk overwriting
     * other participants' data with this submission's necessarily-thin view of their rows.
     *
     * @throws PortalException always, when {@code rowKey} is {@code null}/empty
     */
    void requireResolvedRowKey(Map<String, Object> rowKey) {
        if (rowKey == null || rowKey.isEmpty()) {
            throw new PortalException("MI_ROW_KEY_UNRESOLVED",
                    "Unable to resolve this multi-instance sub-task's own row — refusing to save "
                            + "to avoid overwriting other participants' data. Please reload and try again.");
        }
    }

    /**
     * Merges only the row matching {@code rowKey} from each submitted {@code __subTables__} slice
     * into the corresponding baseline (already-persisted) slice, leaving every other row in the
     * baseline untouched — regardless of what the submitted slice contains for those other rows.
     *
     * <p>Also patches every OTHER numeric {@code dw_form_table_bindings} alias of the same designer
     * table that is absent from the submission (see {@link #findSiblingBindingIdKeys}). A single
     * designer sub-table is bound once per Task/Process Design form — e.g. an MI collection table
     * bound by "Assign Task", "Sub task", "Sub task (My Request)", "Main", … — and each binding gets
     * its own numeric key in {@code __subTables__}. The submitting sub-task's own binding (and the
     * shared string aliases such as table name) are the only keys present in {@code submittedSubTables};
     * every sibling numeric key is left stale in the baseline unless patched here too, which used to
     * let the frontend's own cross-binding-pooling fallbacks (e.g. resolving "my row" by scanning all
     * sibling slices for a PK match) resurrect a sibling's stale copy of this exact row ahead of the
     * freshly-saved one — a 3rd occurrence of the same class of cross-binding contamination.
     *
     * @param submittedSubTables {@code editableData.get("__subTables__")}, already field-permission
     *                           filtered; every alias key (numeric bindingId, table name,
     *                           normalized name, …) present in the submission is processed
     * @param baselineSubTables  the process instance's currently-persisted {@code __subTables__}
     *                           map (may be {@code null}/empty for a brand-new process)
     * @param rowKey             the current MI sub-task's own row PK, from
     *                           {@link #resolveCurrentItemRowKey}; must be non-null/non-empty —
     *                           callers must not invoke this method otherwise (an unresolvable row
     *                           key on a confirmed MI submission must fail the save outright, via
     *                           {@link #requireResolvedRowKey}, before this method is ever reached)
     * @return a new map with the submitted keys plus any patched sibling numeric keys, each value
     *         replaced by the row-merged array
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> mergeCurrentRowOnly(
            Map<String, Object> submittedSubTables,
            Map<String, Object> baselineSubTables,
            Map<String, Object> rowKey) {
        List<String> pkCols = List.copyOf(rowKey.keySet());
        Map<String, Object> merged = new LinkedHashMap<>();
        if (submittedSubTables == null) {
            return merged;
        }
        Map<String, Object> baseline = baselineSubTables != null ? baselineSubTables : Map.of();
        for (Map.Entry<String, Object> entry : submittedSubTables.entrySet()) {
            String key = entry.getKey();
            Object submittedValue = entry.getValue();
            if (!(submittedValue instanceof List<?> submittedRows)) {
                // Nested/non-array shapes (e.g. a row's own __subTables__) are not this MI
                // collection's row array — pass through unchanged, same as legacy putAll did.
                merged.put(key, submittedValue);
                continue;
            }
            if (!isParticipantScopedBinding(key)) {
                // Not scoped to one MI participant's row (e.g. a shared attachment table keyed by
                // main_id to the whole request) — row-scoping this by the current sub-task's
                // participant PK would never find a match and silently discard every submitted row.
                // Pass the submission through unchanged, same as legacy putAll for this key.
                merged.put(key, submittedValue);
                continue;
            }
            Object baselineValue = baseline.get(key);
            List<Object> baselineRows = baselineValue instanceof List<?> l
                    ? new ArrayList<>((List<Object>) l)
                    : new ArrayList<>();
            List<Object> submittedRowsList = (List<Object>) submittedRows;
            merged.put(key, mergeRowsKeepingBaselineExceptCurrent(baselineRows, submittedRowsList, pkCols, rowKey));

            for (String siblingKey : findSiblingBindingIdKeys(key, baseline.keySet())) {
                if (merged.containsKey(siblingKey) || submittedSubTables.containsKey(siblingKey)) {
                    continue;
                }
                Object siblingBaselineValue = baseline.get(siblingKey);
                List<Object> siblingBaselineRows = siblingBaselineValue instanceof List<?> l
                        ? new ArrayList<>((List<Object>) l)
                        : new ArrayList<>();
                merged.put(siblingKey,
                        mergeRowsKeepingBaselineExceptCurrent(siblingBaselineRows, submittedRowsList, pkCols, rowKey));
            }
        }
        return merged;
    }

    /**
     * Other {@code dw_form_table_bindings.id} values (as {@code __subTables__} string keys) bound to
     * the same designer table as {@code submittedKey}, restricted to keys actually present in
     * {@code baselineKeys} (no point patching a key the baseline never had). Returns an empty set
     * when {@code submittedKey} is not itself a numeric binding id (string table-name aliases carry
     * no binding id to look up siblings from) or when the lookup fails for any reason — sibling
     * patching is a best-effort de-staleness measure, not a correctness requirement enforced by
     * {@link #requireResolvedRowKey}.
     */
    /**
     * True unless {@code submittedSubTables} key resolves to a binding whose {@code foreign_key_field}
     * marks it as scoped to the whole process/request rather than to one MI participant row (see
     * {@link #SHARED_PROCESS_SUB_TABLE_FK}). Defaults to {@code true} (participant-scoped, i.e. row-merge
     * as before) when the key cannot be resolved to a binding at all — the common case for this method
     * is the MI collection table itself, so an unresolvable key is far more likely to be a legitimate
     * participant table under an alias this lookup doesn't recognize than a new shared-table shape;
     * defaulting to "not scoped" would instead silently stop row-isolating the exact table class this
     * whole mechanism exists to protect.
     */
    private boolean isParticipantScopedBinding(String submittedKey) {
        try {
            String foreignKeyField;
            Long bindingId = parseLongOrNull(submittedKey);
            if (bindingId != null) {
                List<String> fk = jdbcTemplate.queryForList(
                        "SELECT foreign_key_field FROM dw_form_table_bindings WHERE id = ?",
                        String.class, bindingId);
                foreignKeyField = fk.isEmpty() ? null : fk.get(0);
                if (fk.isEmpty()) {
                    // Not a real binding id (e.g. it just happens to parse as a number) — fall
                    // through to the table-name lookup below using the same raw key.
                    foreignKeyField = lookupForeignKeyFieldByTableName(submittedKey);
                }
            } else {
                foreignKeyField = lookupForeignKeyFieldByTableName(submittedKey);
            }
            if (foreignKeyField == null) {
                return true;
            }
            return !SHARED_PROCESS_SUB_TABLE_FK.contains(foreignKeyField.trim().toLowerCase());
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private String lookupForeignKeyFieldByTableName(String tableName) {
        List<String> fk = jdbcTemplate.queryForList("""
                SELECT b.foreign_key_field
                FROM dw_form_table_bindings b
                JOIN dw_table_definitions t ON t.id = b.table_id
                WHERE lower(t.table_name) = lower(?)
                LIMIT 1
                """, String.class, tableName);
        return fk.isEmpty() ? null : fk.get(0);
    }

    private Set<String> findSiblingBindingIdKeys(String submittedKey, Set<String> baselineKeys) {
        Long bindingId = parseLongOrNull(submittedKey);
        if (bindingId == null) {
            return Set.of();
        }
        try {
            List<Long> siblingIds = jdbcTemplate.queryForList("""
                    SELECT sibling.id
                    FROM dw_form_table_bindings binding
                    JOIN dw_form_table_bindings sibling ON sibling.table_id = binding.table_id
                    WHERE binding.id = ? AND binding.table_id IS NOT NULL AND sibling.id <> binding.id
                    """, Long.class, bindingId);
            if (siblingIds == null || siblingIds.isEmpty()) {
                return Set.of();
            }
            Set<String> result = new HashSet<>();
            for (Long siblingId : siblingIds) {
                String key = String.valueOf(siblingId);
                if (baselineKeys.contains(key)) {
                    result.add(key);
                }
            }
            return result;
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    private Long parseLongOrNull(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> mergeRowsKeepingBaselineExceptCurrent(
            List<Object> baselineRows,
            List<Object> submittedRows,
            List<String> pkCols,
            Map<String, Object> rowKey) {
        Object submittedCurrentRow = null;
        for (Object row : submittedRows) {
            if (!(row instanceof Map)) {
                continue;
            }
            Map<String, Object> rowMap = (Map<String, Object>) row;
            Map<String, Object> candidateKey = SubTableRowKeySupport.rowKeyFromVariableRow(rowMap, pkCols);
            if (rowKeysEqual(candidateKey, rowKey)) {
                submittedCurrentRow = rowMap;
                break;
            }
        }
        if (submittedCurrentRow == null) {
            // Current row absent from submission (shouldn't happen — the form always carries its
            // own row) — nothing of ours to merge in; baseline stands untouched.
            return baselineRows;
        }

        List<Object> result = new ArrayList<>();
        boolean replaced = false;
        for (Object row : baselineRows) {
            if (row instanceof Map) {
                Map<String, Object> rowMap = (Map<String, Object>) row;
                Map<String, Object> candidateKey = SubTableRowKeySupport.rowKeyFromVariableRow(rowMap, pkCols);
                if (rowKeysEqual(candidateKey, rowKey)) {
                    result.add(submittedCurrentRow);
                    replaced = true;
                    continue;
                }
            }
            result.add(row);
        }
        if (!replaced) {
            // New participant row not yet in the baseline — append rather than drop it.
            result.add(submittedCurrentRow);
        }
        return result;
    }

    private boolean rowKeysEqual(Map<String, Object> a, Map<String, Object> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, Object> e : a.entrySet()) {
            Object bv = b.get(e.getKey());
            if (bv == null || !String.valueOf(bv).equals(String.valueOf(e.getValue()))) {
                return false;
            }
        }
        return true;
    }
}
