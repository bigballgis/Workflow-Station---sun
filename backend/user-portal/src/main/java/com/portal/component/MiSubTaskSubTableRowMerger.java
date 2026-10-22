package com.portal.component;

import com.platform.common.jdbc.SubTableRowKeySupport;
import com.platform.common.subtable.SubTableStoreKeys;
import com.portal.dto.SubTableBindingScope;
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
     * {@code MiCollectionVariableBuilder} (PK from the request's pinned release, which throws
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
     *
     * <p>This and the shorter overloads below carry no release facts, so any unscoped {@code dw:} slice is
     * refused ({@code VERSION_RECOVERY_REQUIRED}); production passes the release through the full overload.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> mergeCurrentRowOnly(
            Map<String, Object> submittedSubTables,
            Map<String, Object> baselineSubTables,
            Map<String, Object> rowKey) {
        return mergeCurrentRowOnly(submittedSubTables, baselineSubTables, rowKey, Set.of(), List.of());
    }

    /**
     * @param explicitlyEmptiedKeys {@code __subTables__} store keys the frontend rendered, scoped to
     *                              this participant, and submitted as EMPTY on purpose — i.e. the
     *                              user deleted the last row they owned. Only these keys may clear
     *                              the participant's baseline rows; every other empty slice leaves
     *                              the baseline untouched (see the empty-slice branch below).
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> mergeCurrentRowOnly(
            Map<String, Object> submittedSubTables,
            Map<String, Object> baselineSubTables,
            Map<String, Object> rowKey,
            Set<String> explicitlyEmptiedKeys) {
        return mergeCurrentRowOnly(
                submittedSubTables, baselineSubTables, rowKey, explicitlyEmptiedKeys, List.of());
    }

    /**
     * @param bindingScopes per-binding write claims from the request. Empty keeps the table-keyed
     *                      classifier. When present, this form's bindings decide shared vs
     *                      participant vs mixed — not every binding that exists on the table.
     */
    Map<String, Object> mergeCurrentRowOnly(
            Map<String, Object> submittedSubTables,
            Map<String, Object> baselineSubTables,
            Map<String, Object> rowKey,
            Set<String> explicitlyEmptiedKeys,
            List<SubTableBindingScope> bindingScopes) {
        return mergeCurrentRowOnly(
                submittedSubTables, baselineSubTables, rowKey, explicitlyEmptiedKeys, bindingScopes, null);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> mergeCurrentRowOnly(
            Map<String, Object> submittedSubTables,
            Map<String, Object> baselineSubTables,
            Map<String, Object> rowKey,
            Set<String> explicitlyEmptiedKeys,
            List<SubTableBindingScope> bindingScopes,
            String functionUnitCode) {
        return mergeCurrentRowOnly(submittedSubTables, baselineSubTables, rowKey,
                explicitlyEmptiedKeys, bindingScopes, functionUnitCode, null, null);
    }

    /**
     * @param tableFacts the pinned release's tables ({@link SubTableWriteDesign#tableFacts}); a slice
     *                   with no binding scope is classified from these, never from the live designer
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> mergeCurrentRowOnly(Map<String, Object> submittedSubTables,
            Map<String, Object> baselineSubTables, Map<String, Object> rowKey,
            Set<String> explicitlyEmptiedKeys, List<SubTableBindingScope> bindingScopes,
            String functionUnitCode, Map<String, SubTableWriteDesign.Binding> frozen,
            Map<String, SubTableWriteDesign.TableFacts> tableFacts) {
        Set<String> emptied = explicitlyEmptiedKeys != null ? explicitlyEmptiedKeys : Set.<String>of();
        List<String> pkCols = List.copyOf(rowKey.keySet());
        Map<String, Object> merged = new LinkedHashMap<>();
        if (submittedSubTables == null) {
            return merged;
        }
        Map<String, Object> baseline = baselineSubTables != null ? baselineSubTables : Map.of();
        Map<String, List<SubTableBindingScope>> scopesByKey =
                MiSubTableScopeAwareMerge.indexByStoreKey(bindingScopes);
        for (Map.Entry<String, Object> entry : submittedSubTables.entrySet()) {
            String key = entry.getKey();
            Object submittedValue = entry.getValue();
            if (!(submittedValue instanceof List<?> submittedRows)) {
                // Nested/non-array shapes (e.g. a row's own __subTables__) are not this MI
                // collection's row array — pass through unchanged, same as legacy putAll did.
                merged.put(key, submittedValue);
                continue;
            }
            Object baselineValue = baseline.get(key);
            List<Object> baselineRows = baselineValue instanceof List<?> l
                    ? new ArrayList<>((List<Object>) l)
                    : new ArrayList<>();
            List<Object> submittedRowsList = (List<Object>) submittedRows;
            List<SubTableBindingScope> keyScopes = scopesByKey.getOrDefault(key, List.of());
            if (!keyScopes.isEmpty()) {
                merged.put(key, MiSubTableScopeAwareMerge.apply(
                        jdbcTemplate, key, submittedRowsList, baselineRows, rowKey, keyScopes,
                        emptied.contains(key), functionUnitCode,
                        (fk, explEmptied) -> mergeRowsKeepingBaselineExceptCurrent(
                                baselineRows, submittedRowsList, pkCols, rowKey, fk, explEmptied), frozen));
                continue;
            }
            if (!isParticipantScopedBinding(key, tableFacts)) {
                // Not scoped to one MI participant's row (e.g. a shared attachment table keyed by
                // main_id to the whole request) — row-scoping this by the current sub-task's
                // participant PK would never find a match and silently discard every submitted row.
                // Pass the submission through unchanged, same as legacy putAll for this key.
                merged.put(key, submittedValue);
                continue;
            }
            merged.put(key, mergeRowsKeepingBaselineExceptCurrent(
                    baselineRows, submittedRowsList, pkCols, rowKey,
                    foreignKeyColumns(key, tableFacts), emptied.contains(key)));
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
     * Whether this slice is one MI participant's row set (row-merge) rather than shared by the whole
     * request (pass through). Decided from the pinned release's tables, never from the live designer.
     */
    private boolean isParticipantScopedBinding(String submittedKey,
                                               Map<String, SubTableWriteDesign.TableFacts> tableFacts) {
        // A relation-table slice (`rt:<name>`) is never one MI participant's row set: relation tables
        // are not designer sub-tables, carry no participant FK, and legitimately have no primary key.
        // Row-scoping one by the current sub-task's participant PK finds no match and — since the
        // slice is non-empty — fails the whole save. Measured: a form binding relation table `test`
        // alongside the MI sub-table made every Save on that task return 500, even though the
        // sub-task's own PK (`id_idwnn`) was configured correctly and submitted correctly.
        if (submittedKey != null && submittedKey.startsWith(SubTableStoreKeys.RT_PREFIX)) {
            return false;
        }
        if (submittedKey == null || !submittedKey.startsWith(SubTableStoreKeys.DW_PREFIX)
                || submittedKey.length() == SubTableStoreKeys.DW_PREFIX.length()) {
            return true;   // legacy non-canonical key: keep the participant guard on, as before
        }
        SubTableWriteDesign.TableFacts facts = releaseFacts(submittedKey, tableFacts);
        // Not a designer table of this release — including LEGACY `dw:` keys naming a relation table.
        // Before `relationTableId` reached the binding payload the portal filed relation tables under
        // `dw:<name>` too; those keys still sit in persisted `__subTables__`. Measured on FU
        // fu-20260422: relation table `test` arrived as `dw:test` and every Save on that task returned 500.
        if (facts == null || facts.relation()) {
            return false;
        }
        // 判据 1（最高优先）：设计器把这张表**显式声明**为 MI 参与者行集合
        // （Manage Table Bindings 里 Link Mode = MI Participant Row）。
        //
        // 必须排在结构判据前面：collection 表自己通常也有一个指向主表的外键
        // （demo FU 的 subtable.main_idaaz -> main），只看「FK 指向 MAIN」会把 collection
        // 判成共享表 → 整片跳过行合并 → 当前参与者提交的 thin stub 直接盖掉兄弟参与者
        // 已保存的字段（实测：Test-000003 的 name 被覆盖成空串）。这正是本类要防的事故。
        if (facts.participantRow()) {
            return true;
        }
        // 判据 2：是不是「整个请求共享」的表，按**结构事实**判定：它的外键指向 MAIN 表，
        // 而不是指向 MI 子任务表。查不出或互相矛盾时保持参与者守卫开着。
        Boolean pointsAtMainTable = pointsAtMainTable(facts.foreignKeyTargetTypes());
        return pointsAtMainTable == null || !pointsAtMainTable;
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

    /**
     * Whether the table's foreign keys point at the MAIN table — the structural definition of "shared
     * by the whole request". {@code targetTypes} are the bindings' declared filter FKs when there are
     * any, otherwise every FK of the table. When they disagree (some MAIN, some not) a table-keyed
     * payload cannot say which binding the rows belong to, so this returns {@code null} and the caller
     * keeps the participant guard on rather than guessing.
     */
    private static Boolean pointsAtMainTable(List<String> targetTypes) {
        if (targetTypes == null || targetTypes.isEmpty()) {
            return null;
        }
        boolean anyMain = targetTypes.stream().anyMatch("MAIN"::equalsIgnoreCase);
        boolean anyNonMain = targetTypes.stream().anyMatch(t -> t != null && !"MAIN".equalsIgnoreCase(t));
        if (anyMain && anyNonMain) {
            return null;
        }
        return anyMain;
    }

    /**
     * Foreign-key column names the release declares on the table a canonical store key names. This is
     * what decides "which column points at the participant", and it must come from the design, never
     * from a list of likely names (renamed keys such as sub_task_id → sub_task_idqc missed every guess).
     */
    private List<String> foreignKeyColumns(String storeKey, Map<String, SubTableWriteDesign.TableFacts> tableFacts) {
        if (storeKey == null || !storeKey.startsWith(SubTableStoreKeys.DW_PREFIX)) {
            return List.of();
        }
        SubTableWriteDesign.TableFacts facts = releaseFacts(storeKey, tableFacts);
        return facts == null ? List.of() : facts.foreignKeyFields();
    }

    /** The pinned release's facts for this table; {@code null} when the release has no such table. */
    private static SubTableWriteDesign.TableFacts releaseFacts(String storeKey,
                                                            Map<String, SubTableWriteDesign.TableFacts> tableFacts) {
        if (tableFacts == null) {
            throw new com.platform.common.functionunit.FunctionUnitVersionException(
                    "VERSION_RECOVERY_REQUIRED", storeKey);
        }
        return tableFacts.get(storeKey.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * True when this row points at the current MI participant through a structural FK.
     *
     * <p>The participant's identity is the VALUE of the collection row key (e.g.
     * {@code {id_idwnn: "Test-000002"}} → {@code "Test-000002"}); a link-child row carries that same
     * value in one of its designer-declared FK columns. Composite collection keys are not
     * expressible as a single FK value, so those fall through to PK matching unchanged.
     *
     * <p>{@code fkColumns} comes from {@link #foreignKeyColumns} — the release's
     * own declaration. An empty list means "this caller could not resolve the table's FK columns",
     * and the answer is a plain {@code false}: refuse to guess rather than fall back to column-name
     * literals, which silently mis-answered once the keys were renamed.
     */
    private boolean rowBelongsToParticipantByStructuralFk(
            Map<String, Object> rowMap, Map<String, Object> rowKey, List<String> fkColumns) {
        if (rowMap == null || rowKey == null || rowKey.size() != 1) {
            return false;
        }
        Object participantId = rowKey.values().iterator().next();
        if (participantId == null || String.valueOf(participantId).isBlank()) {
            return false;
        }
        String want = String.valueOf(participantId).trim();
        for (String fk : fkColumns) {
            Object v = SubTableRowKeySupport.getRowValueIgnoreCase(rowMap, fk);
            if (v != null && want.equals(String.valueOf(v).trim())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<Object> mergeRowsKeepingBaselineExceptCurrent(
            List<Object> baselineRows,
            List<Object> submittedRows,
            List<String> pkCols,
            Map<String, Object> rowKey,
            List<String> fkColumns,
            boolean explicitlyEmptied) {
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
            // A link-child table (People-style) does not carry the COLLECTION's primary key: its own
            // PK is its row id (a UUID) and it points at the participant through a structural FK
            // (sub_task_id / participant_id / …). Matching by pkCols therefore never succeeds and the
            // guard below rejected the whole save. Measured: `people` rows
            // {id=<uuid>, sub_task_id=Test-000002, …} failed every Save on that sub-task.
            //
            // Such a table is NOT one-row-per-participant — a participant may own several rows — so it
            // cannot go through the single-row replace below without dropping every row but the first.
            // Replace this participant's whole set instead, leaving other participants' rows untouched.
            List<Object> ownByFk = new ArrayList<>();
            for (Object row : submittedRows) {
                if (row instanceof Map
                        && rowBelongsToParticipantByStructuralFk((Map<String, Object>) row, rowKey, fkColumns)) {
                    ownByFk.add(row);
                }
            }
            if (!ownByFk.isEmpty()) {
                List<Object> out = new ArrayList<>();
                for (Object row : baselineRows) {
                    if (row instanceof Map
                            && rowBelongsToParticipantByStructuralFk(
                                    (Map<String, Object>) row, rowKey, fkColumns)) {
                        continue;   // drop this participant's stale rows; the submission replaces them
                    }
                    out.add(row);
                }
                out.addAll(ownByFk);
                return out;
            }
            // The user deleted every row they owned on this table.
            //
            // Reaching here means the submission contains NO row of ours (ownByFk was empty). That
            // is AMBIGUOUS on its own: it means either "the user deleted the last row they owned" or
            // "this key has nothing to contribute" (the form does not render this binding, MI
            // isolation rebuilt the payload without it, this participant simply has no row yet, …).
            //
            // Guessing either way is wrong in one direction: always keeping the baseline makes
            // deleting a participant's LAST row impossible (measured — deleting one of several
            // People rows persisted, deleting the only one silently came back on reload), while
            // always clearing would wipe rows for a binding that was merely absent from the form.
            //
            // So the frontend states its intent explicitly: `explicitlyEmptiedKeys` names the keys it
            // rendered, scoped to this participant, and emptied. Absent that declaration the baseline
            // stands, exactly as before.
            //
            // NOTE this is deliberately checked BEFORE the empty/all-foreign branches below: the
            // submitted slice is normally NOT empty even when the user deleted their last row,
            // because it still carries the other participants' rows (measured: submitted=3 peers,
            // baseline=4). An `isEmpty()`-only guard therefore never fired in practice.
            //
            // It requires resolvable FK columns. Deleting is expressed here as "drop the baseline
            // rows whose structural FK names me"; with no FK columns that predicate is a constant
            // false, so the delete would be a silent no-op — and, worse, checking this branch ahead
            // of the MI_ROW_KEY_UNRESOLVED guard below would swallow the very corruption that guard
            // exists to report. Without FK metadata, fall through and let the old branches decide.
            if (explicitlyEmptied && !fkColumns.isEmpty()) {
                List<Object> out = new ArrayList<>();
                for (Object row : baselineRows) {
                    if (row instanceof Map
                            && rowBelongsToParticipantByStructuralFk(
                                    (Map<String, Object>) row, rowKey, fkColumns)) {
                        continue;   // the user deleted this participant's rows; honour it
                    }
                    out.add(row);   // other participants' rows are untouched
                }
                // Peers keep their PERSISTED rows verbatim. This submission is authoritative only
                // about the rows it owns; a sibling's row reaches an MI sub-task thinned down to
                // identity fields (see the class doc), so preferring the submitted copy here would
                // overwrite a peer's data with stubs — the exact loss this class exists to prevent.
                return out;
            }
            if (submittedRows.isEmpty()) {
                // Empty and undeclared: nothing of ours to merge in, so the baseline stands.
                return baselineRows;
            }
            // Every submitted row provably belongs to ANOTHER participant (each carries a structural
            // FK naming someone else). That is not corruption — it is a link-child table on which
            // this participant simply has no row yet, while a sibling's row rides along in the shared
            // slice. Measured: participant Test-000002 opened a task whose `people` slice held only
            // {sub_task_id: Test-000001}; failing here rejected every Save even though nothing of
            // ours was at stake. Leave the baseline untouched, exactly like the empty-slice case.
            boolean everySubmittedRowBelongsToAnotherParticipant = true;
            for (Object row : submittedRows) {
                if (!(row instanceof Map)) {
                    everySubmittedRowBelongsToAnotherParticipant = false;
                    break;
                }
                Map<String, Object> rowMap = (Map<String, Object>) row;
                String otherFk = null;
                for (String fk : fkColumns) {
                    Object v = SubTableRowKeySupport.getRowValueIgnoreCase(rowMap, fk);
                    if (v != null && !String.valueOf(v).trim().isEmpty()) {
                        otherFk = String.valueOf(v).trim();
                        break;
                    }
                }
                // No structural FK at all, or one naming US, means this row is not provably foreign.
                if (otherFk == null || rowBelongsToParticipantByStructuralFk(rowMap, rowKey, fkColumns)) {
                    everySubmittedRowBelongsToAnotherParticipant = false;
                    break;
                }
            }
            if (everySubmittedRowBelongsToAnotherParticipant) {
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
