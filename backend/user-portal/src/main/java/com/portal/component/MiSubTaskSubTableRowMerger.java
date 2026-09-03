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
        return mergeCurrentRowOnly(submittedSubTables, baselineSubTables, rowKey, Set.of());
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
        Set<String> emptied = explicitlyEmptiedKeys != null ? explicitlyEmptiedKeys : Set.<String>of();
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
            merged.put(key, mergeRowsKeepingBaselineExceptCurrent(
                    baselineRows, submittedRowsList, pkCols, rowKey,
                    lookupForeignKeyColumnsByStoreKey(key), emptied.contains(key)));
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
     * {@link #foreignKeyTargetsMainTable}). Defaults to {@code true} (participant-scoped, i.e. row-merge
     * as before) when the key cannot be resolved to a binding at all — the common case for this method
     * is the MI collection table itself, so an unresolvable key is far more likely to be a legitimate
     * participant table under an alias this lookup doesn't recognize than a new shared-table shape;
     * defaulting to "not scoped" would instead silently stop row-isolating the exact table class this
     * whole mechanism exists to protect.
     */
    private boolean isParticipantScopedBinding(String submittedKey) {
        // A relation-table slice (`rt:<name>`) is never one MI participant's row set: relation tables
        // are not designer sub-tables, carry no participant FK, and legitimately have no primary key.
        // Row-scoping one by the current sub-task's participant PK finds no match and — since the
        // slice is non-empty — fails the whole save. Measured: a form binding relation table `test`
        // alongside the MI sub-table made every Save on that task return 500, even though the
        // sub-task's own PK (`id_idwnn`) was configured correctly and submitted correctly.
        if (submittedKey != null && submittedKey.startsWith(SubTableStoreKeys.RT_PREFIX)) {
            return false;
        }
        try {
            // Belt-and-braces for LEGACY `dw:` keys naming a relation table.
            //
            // The `rt:` check above handles anything written since `relationTableId` was added to the
            // binding payload. Before that the portal could not tell the two apart and filed relation
            // tables under `dw:<name>` too — those keys are still sitting in persisted
            // `__subTables__` and keep arriving on submit. Measured on FU fu-20260422: binding 50550
            // is relation table `test` (dw_form_table_bindings.relation_table_id=1) yet arrived as
            // `dw:test`, and every Save on that task returned 500 — while the MI sub-table's own PK
            // (`id_idwnn`) was configured and submitted correctly.
            if (!isDesignerSubTableStoreKey(submittedKey)) {
                return false;
            }
            // 判据 1（最高优先）：设计器把这张表**显式声明**为 MI 参与者行集合
            // （Manage Table Bindings 里 Link Mode = MI Participant Row）。
            //
            // 必须排在结构判据前面：collection 表自己通常也有一个指向主表的外键
            // （demo FU 的 subtable.main_idaaz -> main），只看「FK 指向 MAIN」会把 collection
            // 判成共享表 → 整片跳过行合并 → 当前参与者提交的 thin stub 直接盖掉兄弟参与者
            // 已保存的字段（实测：Test-000003 的 name 被覆盖成空串）。这正是本类要防的事故。
            if (bindingDeclaredAsMiParticipantRow(submittedKey)) {
                return true;
            }
            // 判据 2：是不是「整个请求共享」的表，按**结构事实**判定：它的外键指向 MAIN 表，
            // 而不是指向 MI 子任务表。曾经这里比对一张写死的列名表（main_id / process_id / …），
            // 改名后（attachment 的外键成了 main_idva）一个都命中不了 —— 共享附件表会被当成
            // 参与者子表逐行隔离，正是本方法注释自己警告的那种静默丢数据。
            Boolean pointsAtMainTable = foreignKeyTargetsMainTable(submittedKey);
            if (pointsAtMainTable == null) {
                // 查不出外键指向：保持参与者守卫开着（与 isDesignerSubTableStoreKey 同样的保守方向）
                return true;
            }
            return !pointsAtMainTable;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /**
     * Whether this canonical key names a table that exists in {@code dw_table_definitions} — i.e. a
     * real designer table rather than a relation table (or the platform's virtual {@code sys_users}).
     *
     * <p>Unknown / unresolvable names answer {@code true} so the participant guard stays on: this
     * check exists to EXCLUDE things we can prove are not designer tables, never to weaken isolation
     * for something we merely failed to look up.
     */
    private boolean isDesignerSubTableStoreKey(String storeKey) {
        if (storeKey == null || !storeKey.startsWith(SubTableStoreKeys.DW_PREFIX)) {
            return true;
        }
        String tableName = storeKey.substring(SubTableStoreKeys.DW_PREFIX.length());
        if (tableName.isBlank()) {
            return true;
        }
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dw_table_definitions WHERE lower(table_name) = lower(?)",
                Integer.class, tableName);
        return n == null || n > 0;
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
     * Did the designer explicitly declare this table as the MI participant-row collection?
     *
     * <p>{@code dw_form_table_bindings.binding_link_mode = 'miParticipantRow'} is the Developer
     * Workstation's "Link Mode = MI Participant Row" setting — an explicit statement, not an
     * inference, which is why it outranks every structural check.
     *
     * <p>It has to outrank {@link #foreignKeyTargetsMainTable} specifically: a collection table
     * normally carries its own foreign key to the main table too (demo FU:
     * {@code subtable.main_idaaz -> main}), so judging by "FK points at MAIN" alone classifies the
     * collection as a shared table, skips row merging for it entirely, and lets one participant's
     * thin stubs overwrite the fields every sibling had already saved.
     *
     * <p>Mirrors the frontend's {@code bindingDeclaresMiParticipantRow}
     * ({@code miBindingKindFromConfig.ts}), whose classifier checks this same declaration first.
     */
    private boolean bindingDeclaredAsMiParticipantRow(String storeKey) {
        if (storeKey == null || !storeKey.startsWith(SubTableStoreKeys.DW_PREFIX)) {
            return false;
        }
        String tableName = storeKey.substring(SubTableStoreKeys.DW_PREFIX.length());
        Integer n = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM dw_form_table_bindings b
                JOIN dw_table_definitions t ON t.id = b.table_id
                WHERE lower(t.table_name) = lower(?) AND b.binding_link_mode = 'miParticipantRow'
                """, Integer.class, tableName);
        return n != null && n > 0;
    }

    /**
     * Does this table's designer-declared foreign key point at the MAIN table?
     *
     * <p>That is the structural definition of "shared by the whole request": an attachment or an
     * action table FK'd to the request itself, as opposed to a link-child FK'd to the MI collection.
     * Column names cannot tell these apart — only the FK's target can — which is why the old
     * {@code main_id}/{@code process_id} literal list broke the moment the demo FU renamed its keys.
     *
     * @return {@code null} when the table or its FK target cannot be resolved (caller keeps the
     *         participant guard on rather than guessing)
     */
    private Boolean foreignKeyTargetsMainTable(String storeKey) {
        if (storeKey == null || !storeKey.startsWith(SubTableStoreKeys.DW_PREFIX)) {
            return null;
        }
        String tableName = storeKey.substring(SubTableStoreKeys.DW_PREFIX.length());
        List<String> refTypes = jdbcTemplate.queryForList("""
                SELECT ref.table_type
                FROM dw_field_definitions f
                JOIN dw_table_definitions t ON t.id = f.table_id
                JOIN dw_table_definitions ref ON ref.id = f.ref_table_id
                WHERE lower(t.table_name) = lower(?) AND f.is_foreign_key = true
                """, String.class, tableName);
        if (refTypes.isEmpty()) {
            return null;
        }
        return refTypes.stream().anyMatch("MAIN"::equalsIgnoreCase);
    }

    /**
     * Foreign-key column names the DESIGNER declared on the table a canonical store key names.
     *
     * <p>This is what decides "which column points at the participant", and it must come from the
     * design, never from a list of likely names. Renaming the demo FU's keys (sub_task_id →
     * sub_task_idqc) made every guessed name miss: the merger then could neither find this
     * participant's own rows nor prove the submitted rows were someone else's, so it failed the
     * save outright with MI_ROW_KEY_UNRESOLVED while the payload was in fact correct.
     */
    private List<String> lookupForeignKeyColumnsByStoreKey(String storeKey) {
        if (storeKey == null || !storeKey.startsWith(SubTableStoreKeys.DW_PREFIX)) {
            return List.of();
        }
        String tableName = storeKey.substring(SubTableStoreKeys.DW_PREFIX.length());
        return jdbcTemplate.queryForList("""
                SELECT f.field_name
                FROM dw_field_definitions f
                JOIN dw_table_definitions t ON t.id = f.table_id
                WHERE lower(t.table_name) = lower(?) AND f.is_foreign_key = true
                """, String.class, tableName);
    }

    /**
     * True when this row points at the current MI participant through a structural FK.
     *
     * <p>The participant's identity is the VALUE of the collection row key (e.g.
     * {@code {id_idwnn: "Test-000002"}} → {@code "Test-000002"}); a link-child row carries that same
     * value in one of its designer-declared FK columns. Composite collection keys are not
     * expressible as a single FK value, so those fall through to PK matching unchanged.
     *
     * <p>{@code fkColumns} comes from {@link #lookupForeignKeyColumnsByStoreKey} — the designer's
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
