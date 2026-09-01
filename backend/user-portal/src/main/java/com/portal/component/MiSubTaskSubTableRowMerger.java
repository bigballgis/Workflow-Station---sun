package com.portal.component;

import com.platform.common.jdbc.SubTableRowKeySupport;
import com.platform.common.subtable.SubTableStoreKeys;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
 * <p><b>No PK guessing.</b> The row key comes verbatim from {@code _currentItem.rowKey}, whose key
 * set is the designer-configured primary key of the MI collection table (see
 * {@link #resolveCurrentItemRowKey}) — any column name, single or composite. This class never
 * assumes a PK column name. Once the key cannot be resolved, or the submitted rows do not contain
 * the row it identifies, the save fails loud rather than degrading to a whole-array replace or a
 * no-op write.
 *
 * <p><b>Technical debt</b>: MI detection relies on {@code _currentItem}/{@code currentItem} being
 * present in the submitted {@code formData} — an implicit consequence of the frontend's
 * {@code buildCurrentTaskFormSubmitPayload} spreading the whole in-memory form state, not an
 * explicit contract. If a future frontend refactor stops forwarding that key, MI detection here
 * silently reverts to treating the submission as non-MI (whole-array replace) rather than failing
 * loud — same behavior as any other non-MI task, so not a data-loss regression by itself, but the
 * row-isolation guarantee this class exists to provide would quietly stop applying.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MiSubTaskSubTableRowMerger {

    private final JdbcTemplate jdbcTemplate;

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
     * The current MI sub-task's own row key, taken verbatim from the BPMN loop variable's
     * {@code rowKey} map.
     *
     * <p><b>{@code rowKey} is its own authority on which columns form the PK.</b> Both producers
     * build it from the designer's configured primary key for the MI collection table — see
     * {@code MiCollectionVariableBuilder} (PK from {@code resolveMiSubTablePkById}, which throws
     * rather than guess) and {@code SubTableDataInjector} (PK from the physical table). So the
     * key set of that map IS the real PK column list, whatever the designer configured: a single
     * {@code id_idw}, a single string/UUID column under any other name, or a composite key. This
     * method therefore never assumes a column name and never consults the database.
     *
     * <p>Nothing here falls back. Previously this passed a hardcoded {@code ["id_idw"]} into
     * {@link SubTableRowKeySupport#rowKeyFromCurrentItem}, which made every sub-table whose PK was
     * not named {@code id}/{@code id_idw} — and every composite PK — resolve to {@code null} and
     * fail the save outright, even though the correct key was sitting right there in {@code rowKey}.
     * Guessing a PK column is what caused that bug; a partially-resolved key is worse than none,
     * because it would merge into (i.e. overwrite) the wrong participant's row.
     *
     * @return the row key's PK columns/values, or {@code null} when the loop variable carries no
     *         usable {@code rowKey} map (absent, wrong shape, empty, or any PK value null/blank).
     *         Only call when {@link #isMiSubTaskSubmission} is true — a {@code null} result here
     *         means "MI, but broken", not "not MI".
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
        Object rawRowKey = ((Map<String, Object>) raw).get("rowKey");
        if (!(rawRowKey instanceof Map<?, ?> rowKeyMap) || rowKeyMap.isEmpty()) {
            return null;
        }
        Map<String, Object> rowKey = SubTableRowKeySupport.normalizeStringKeyMap(rowKeyMap);
        for (Map.Entry<String, Object> e : rowKey.entrySet()) {
            if (e.getKey().isBlank()
                    || e.getValue() == null
                    || String.valueOf(e.getValue()).trim().isEmpty()) {
                // A blank column name or blank PK value cannot identify a row. Merging on a
                // partial key would silently target the wrong participant's row.
                return null;
            }
        }
        return rowKey;
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
     * <p><b>No sibling patching.</b> Keys are now canonical, one per designer table
     * ({@code dw:<name>} / {@code rt:<name>} — see {@link SubTableStoreKeys}), so a table's rows exist
     * exactly once. Previously a table bound by "Assign Task", "Sub task", "Main", … got a separate
     * numeric key per binding plus table-name aliases; a submission only carried some of them, so the
     * rest stayed stale in the baseline and had to be patched here. That whole class of divergence —
     * and the patching that compensated for it — is gone with the single-source-of-truth key.
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
            String foreignKeyField = lookupForeignKeyFieldByStoreKey(submittedKey);
            if (foreignKeyField == null) {
                return true;
            }
            return !SHARED_PROCESS_SUB_TABLE_FK.contains(foreignKeyField.trim().toLowerCase());
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /**
     * {@code foreign_key_field} of any binding on the table this canonical key names.
     *
     * <p>Every binding of one table shares the same FK role for this purpose, so the first match is
     * representative — and after the single-source-of-truth change a table has exactly one key, so
     * there is no per-binding distinction left to make. Keys are {@code dw:<name>} / {@code rt:<name>}
     * (see {@link SubTableStoreKeys}); RT keys have no {@code dw_form_table_bindings.table_id} row and
     * correctly resolve to {@code null} → treated as participant-scoped, matching the previous default.
     */
    private String lookupForeignKeyFieldByStoreKey(String storeKey) {
        if (storeKey == null || !storeKey.startsWith(SubTableStoreKeys.DW_PREFIX)) {
            return null;
        }
        String tableName = storeKey.substring(SubTableStoreKeys.DW_PREFIX.length());
        List<String> fk = jdbcTemplate.queryForList("""
                SELECT b.foreign_key_field
                FROM dw_form_table_bindings b
                JOIN dw_table_definitions t ON t.id = b.table_id
                WHERE lower(t.table_name) = lower(?) AND b.foreign_key_field IS NOT NULL
                LIMIT 1
                """, String.class, tableName);
        return fk.isEmpty() ? null : fk.get(0);
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
            if (submittedRows.isEmpty()) {
                // An EMPTY slice carries no claim about any row — it means this alias key simply has
                // nothing to contribute (the task form does not render this binding, MI isolation
                // rebuilt the payload without it, …). There is nothing of ours to merge in, so the
                // baseline stands untouched. This is not the corruption case below: no submitted row
                // disagrees with rowKey, so nothing the user typed can be lost by leaving it alone.
                return baselineRows;
            }
            // Non-empty slice that nonetheless lacks our own row: the submitted rows do not agree
            // with rowKey on what identifies a row. Returning the baseline here (as this used to)
            // silently discards everything the user just typed and reports success; fail loud.
            log.warn("[MI] own row {} not found among {} submitted row(s); pkCols={}; submitted rows={}",
                    rowKey, submittedRows.size(), pkCols, submittedRows);
            throw new PortalException("MI_ROW_KEY_UNRESOLVED",
                    "This multi-instance sub-task's own row (" + rowKey + ") is missing from the "
                            + "submitted sub-table data — refusing to save, as saving would discard "
                            + "your changes. Please reload and try again.");
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
            // Deliberately NOT a guess-style fallback: a participant whose row has never been
            // saved genuinely has no baseline row yet, and the very first save must create it.
            // Dropping it (or failing) here would lose that participant's first submission
            // outright. The row key is already proven complete by resolveCurrentItemRowKey, so
            // this appends a row identified by the real PK — it cannot collide with an existing
            // one, since a matching baseline row would have taken the replace branch above.
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
