import { toRaw, triggerRef, markRaw } from 'vue'
import {
  mergeSubTableRowsByRowId,
  stripNestedSubTablesFromRows,
  isMiParticipantScopedSubTableBinding,
  isMiDashboardSubTableBinding,
  isSharedAttachmentFileBinding,
} from '@/composables/tasks/shared'
import {
  resolveAssigneeFieldForBinding,
} from '@/utils/subTableAssignment'
import {
  unwrapUserLikeValueToDisplayString,
  extractUserIdFromCellValue,
} from '@/components/subTableAddDialogHelpers'
import type { MiParticipantRowId } from '@/composables/tasks/miSubProcessScope'
import {
  hasConfiguredPrimaryKeyFields,
  bindingMatchesMiSubTableName,
} from '@/composables/tasks/miParticipantRowKey'
import { writeSubTableRows } from '@/composables/tasks/subTableStore'
import {
  cloneSubTableRows,
  bindingIdsPreferStrictSubTableLookup,
  subTableBindingMatches,
} from './subTableRowUtils'
import type { TaskDetailCtx } from './context'

export interface TaskDetailSyncFns {
  syncMainSubTableRows: (bindingId: number, rows: any[]) => void
  patchFormDataSubTablesFromCurrentBindings: () => void
  rebuildIsolatedSubTablesPayload: (myRowId?: MiParticipantRowId | null) => Record<string, any>
  stripNestedFromAllTaskBindings: () => void
  markBindingRowsNonReactive: () => void
  applyTaskAssigneeNameToMatchingSubTableRows: (taskData: { assignee?: unknown; assigneeName?: unknown }) => void
}

export function createTaskDetailSubTableSync(ctx: TaskDetailCtx): TaskDetailSyncFns {
  const {
    subTableBindings,
    previousForms,
    nodeFormMap,
    isMiSubTaskMode,
    miFullSubTablesSnapshotRef,
  } = ctx
  const { formData, scheduleSubTableAutosave } = ctx.taskForm

  /** Strip nested row.__subTables__ before Vue mounts el-table — only safe AFTER MI isolate completes. */
  function stripNestedFromAllTaskBindings() {
    const strip = (bindings: typeof subTableBindings.value) => {
      for (const b of bindings) {
        if (Array.isArray(b.data)) stripNestedSubTablesFromRows(b.data)
      }
    }
    strip(subTableBindings.value)
    for (const pf of previousForms.value) strip(pf.subTableBindings)
    for (const info of nodeFormMap.value.values()) strip(info.subTableBindings)
  }

  /**
   * After MI isolation, __subTables__ must carry all participants again; current binding only has
   * this MI row.
   *
   * @param myRowId current MI sub-task's own participant row id. For MI collection/dashboard
   *   bindings, a `previousForms` snapshot can carry a STALE copy of this SAME participant's row
   *   (each sub-task node duplicates the whole collection table under its own bindingId — see
   *   `hydrateBindingsRowsFromVariablesBySharedRelationTableId`'s equivalent guard). Since
   *   `mergeSubTableRowsByRowId` never lets an absent/empty field overwrite a filled one, that stale
   *   row would survive even after `subTableBindings.value`'s own (thin, freshly-loaded) row is
   *   ingested last — the current binding has nothing to overwrite it with (#1524-class: reload
   *   showing this participant's own OLD value). Dropping the current participant's row from
   *   `previousForms` ingestion for these bindings closes that gap; other participants' rows in
   *   `previousForms` are unaffected and still backfill normally.
   */
  function rebuildIsolatedSubTablesPayload(myRowId?: MiParticipantRowId | null): Record<string, any> {
    const subTables: Record<string, any> = {}
    /**
     * This participant's own row, exactly as the CURRENT form holds it. It is authoritative: the
     * user just edited it here, so for this row the current form wins over every previous-form
     * snapshot — including on fields the user cleared, which `mergeSubTableRowsByRowId`'s
     * prefer-filled rule would otherwise refuse to blank out.
     *
     * Previously this was handled by DELETING the row from previous-form snapshots and relying on
     * the current form to re-add it. That coupling was unsound: the delete was unconditional but
     * the re-add was not, so whenever the current form's slice did not carry the row it vanished
     * from the payload entirely and the backend rejected the save. Replacing the row after the
     * merge keeps the same "current form wins" outcome with no window in which the row is absent.
     */
    const myRowFromCurrentForm: Record<string, unknown> | null = (() => {
      if (myRowId == null) return null
      for (const b of subTableBindings.value) {
        for (const row of (Array.isArray(b.data) ? b.data : [])) {
          if (row && typeof row === 'object' && ctx.rowBelongsToCurrentMiScope(row, myRowId, b)) {
            return row as Record<string, unknown>
          }
        }
      }
      return null
    })()

    const ingest = (bindings: typeof subTableBindings.value, dropCurrentMiRow: boolean) => {
      const collision = bindingIdsPreferStrictSubTableLookup(bindings)
      for (const binding of bindings) {
        let rawRows = Array.isArray(binding.data) ? binding.data : []
        // Previous-form snapshots are read-only history: their copy of THIS participant's row is
        // stale by definition, and (because slices share table-name alias keys) letting it through
        // is what made the form re-render another sub-task's data mid-edit. Drop it here — the
        // authoritative copy is re-applied from `myRowFromCurrentForm` after the merge below, so
        // unlike the original delete-and-hope this cannot leave the row missing.
        if (dropCurrentMiRow && myRowId != null && myRowFromCurrentForm
            && isMiDashboardSubTableBinding(binding)) {
          rawRows = rawRows.filter((row: any) => !ctx.rowBelongsToCurrentMiScope(row, myRowId, binding))
        }
        const rows = cloneSubTableRows(rawRows)
        const prev = ctx.getSavedSubTableRows(subTables, binding, collision.has(binding.bindingId))
        // An MI participant row can only be identified by the designer primary key. `row_id` is a
        // per-snapshot frontend value — the same physical row carries different ones in the engine
        // variables copy and the portal subTableData copy — so merging on it silently fused two
        // participants' rows and submitted the wrong one. Refuse to build a payload we cannot key
        // correctly rather than guessing: fail loud here, exactly as the backend does on save.
        //
        // "The MI sub-table" is exactly the one named by the BPMN Sub-Task Config's Sub-table ID
        // (`miSubProcessScope.subTableName`) — never inferred from column names or a table called
        // "participants", which would both mis-fire on unrelated tables and miss a correctly
        // configured one under any other name.
        const miScopeTable = ctx.miSubProcessScope.value?.subTableName
        if (miScopeTable
            && bindingMatchesMiSubTableName(binding, miScopeTable)
            && !hasConfiguredPrimaryKeyFields(binding.primaryKeyFields)) {
          ctx.warnMiMissingPrimaryKey(binding)
          throw new Error(
            `MI sub-table "${binding.tableName || binding.bindingId}" has no primary key configured`,
          )
        }
        // Status / current-node mirror columns come from Sub-Task Config too
        // (miTaskStatusField / miTaskCurrentNodeField), never from a fixed column name.
        let merged = mergeSubTableRowsByRowId(prev, rows, binding.primaryKeyFields, {
          statusField: ctx.miSubProcessScope.value?.miTaskStatusField,
          currentNodeField: ctx.miSubProcessScope.value?.miTaskCurrentNodeField,
        })
        // Current form wins outright for this participant's own row (see myRowFromCurrentForm) —
        // but ONLY where that row already exists in this slice. Appending it into a slice that
        // legitimately holds just another participant's row would inject this participant into a
        // foreign binding, which the sub-form then renders as "the other sub-task's data".
        // A slice that never had our row simply keeps what it had; the collection slices that do
        // carry it are the ones the backend row-merges against.
        if (myRowFromCurrentForm && myRowId != null && isMiDashboardSubTableBinding(binding)) {
          const idx = merged.findIndex(
            (row: any) => ctx.rowBelongsToCurrentMiScope(row, myRowId, binding),
          )
          if (idx >= 0) {
            merged[idx] = cloneSubTableRows([myRowFromCurrentForm])[0]
          } else if (dropCurrentMiRow) {
            // Only re-add where we just dropped it (previous-form slices that DID hold this row).
            // Never append into a slice that never had it — that injected this participant into a
            // binding scoped to another sub-task, which the form then rendered as their data.
            const held = (Array.isArray(binding.data) ? binding.data : []).some(
              (row: any) => ctx.rowBelongsToCurrentMiScope(row, myRowId, binding),
            )
            if (held) merged = [...merged, cloneSubTableRows([myRowFromCurrentForm])[0]]
          }
        }
        const out = cloneSubTableRows(merged)
        writeSubTableRows(subTables, binding, out)
      }
    }
    for (const pf of previousForms.value) {
      ingest(pf.subTableBindings, true)
    }
    ingest(subTableBindings.value, false)

    // No cross-key reconciliation needed: every binding of one designer table now writes the
    // SAME canonical key (`dw:<name>` / `rt:<name>`), so a row physically cannot exist in two
    // versions. The sweep that used to hunt for "the authoritative copy" among divergent slices
    // is gone with the divergence it compensated for.
    return subTables
  }

  function syncMainSubTableRows(bindingId: number, rows: any[]) {
    const source = subTableBindings.value.find(b => b.bindingId === bindingId)
    if (!source) return

    const nextRows = Array.isArray(rows) ? rows : []

    const subTables = { ...((formData.value.__subTables__ as Record<string, any>) || {}) }
    const strictSlices = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)
    const existing = ctx.getSavedSubTableRows(subTables, source, strictSlices.has(source.bindingId))
    // `existing` comes from formData.__subTables__, which carries EVERY participant's row (the save
    // payload must submit them all). What this function feeds back into `binding.data` is what the
    // sub-form RENDERS, so merging the whole slice in would put other participants' rows on this
    // sub-task's form — typing one character then re-rendered someone else's data. Keep only this
    // participant's row from the persisted slice; `nextRows` is what the user is editing right now.
    const myRowId = isMiSubTaskMode.value ? ctx.currentMiRowId.value : null
    const existingForMe =
      myRowId != null && Array.isArray(existing)
        ? existing.filter((row: any) => ctx.rowBelongsToCurrentMiScope(row, myRowId, source))
        : existing
    // `nextRows` is what the grid/form currently holds for this participant — the user's live edit,
    // including fields they just cleared. Merging the persisted copy in would re-fill those blanks
    // (prefer-filled), which is exactly why edits appeared to save and then revert. Only fall back
    // to the persisted rows when the editor supplied none at all.
    const merged = isMiSubTaskMode.value
      ? (nextRows.length > 0
          ? nextRows
          : mergeSubTableRowsByRowId(existingForMe, nextRows, source.primaryKeyFields))
      : nextRows
    const out = cloneSubTableRows(merged)

    const sync = (binding: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null; data: any[] }) => {
      if (subTableBindingMatches(binding, source)) {
        binding.data = binding === source ? out : cloneSubTableRows(out)
      }
    }
    subTableBindings.value.forEach(sync)
    // Never push current-task sub-table edits into previousForms — those are read-only snapshots
    // (MI isolation + matching bindingIds would wipe other sub-tasks' rows).

    // `out` is deliberately scoped to this participant (it drives the form). The SAVE payload must
    // still carry every participant, so fold this edit back into the full slice rather than
    // replacing it — otherwise the other participants' rows would be dropped from formData and the
    // submission would look like they were deleted.
    // NOT a merge: `mergeSubTableRowsByRowId` is prefer-filled, so the persisted row's old value
    // would beat the value the user just typed (edits appeared to save and then revert). Take the
    // other participants' rows verbatim from the persisted slice and this participant's row
    // verbatim from `out` — the form is the only authority for the row being edited.
    const outForPayload =
      myRowId != null && Array.isArray(existing) && existing.length > 0
        ? [
            ...existing.filter((row: any) => !ctx.rowBelongsToCurrentMiScope(row, myRowId, source)),
            ...out,
          ]
        : out
    writeSubTableRows(subTables, source, outForPayload)

    if (isMiSubTaskMode.value) {
      // 只有「link-child binding 被编辑」时才需要把它的行回写进父行的嵌套槽。
      // source 本身就是 MI collection（Participants）时不能调：那会把 collection 的行
      // 当成 child slice 塞进其他 binding 的父行嵌套里，把刚写好的嵌套 People 覆盖掉。
      //
      // 实测现场：People 子表嵌在 Participants 的内联表单里，所以新增 People 触发的是
      // syncMainSubTableRows(Participants)，行本身已正确带着 nested People 进来
      // （[SYNCIN] nestedPeople=[1]），却在这一步被抹平，提交 payload 里 nested people = []。
      if (!ctx.isCurrentMiCollectionSubTableBinding(source)) {
        ctx.syncMiLinkChildRowsIntoParentNested(
          { bindingId: source.bindingId, tableName: source.tableName },
          out
        )
      }
      const ambiguous = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)
      for (const parentBinding of subTableBindings.value) {
        if (parentBinding.bindingId === source.bindingId) continue
        const parentRows = cloneSubTableRows(
          Array.isArray(parentBinding.data) ? parentBinding.data : []
        )
        writeSubTableRows(subTables, parentBinding, parentRows)
      }
    }

    formData.value = { ...formData.value, __subTables__: subTables }
    scheduleSubTableAutosave()
  }

  /** After MI refilter, align {@link formData}.__subTables__ keys for current bindings so autosave/submit matches the grid. */
  /**
   * Re-attach each row's nested {@code __subTables__} from the slice being replaced.
   *
   * <p>A sub-table can be rendered inside ANOTHER table's inline form; its rows then live in the
   * host row's nested slice and its own `binding.data` is empty. Any rebuild that starts from
   * `binding.data` would drop that payload, so copy it back per row (matched by designer PK) unless
   * the incoming row already carries one.
   */
  function preserveNestedSubTablesFromExisting(
    existingRows: any[],
    nextRows: any[],
    pkFields: string[] | null,
  ): any[] {
    const pks = (pkFields ?? []).map(f => String(f ?? '').trim()).filter(Boolean)
    if (pks.length === 0 || !Array.isArray(existingRows) || existingRows.length === 0) return nextRows
    const keyOf = (row: any): string | null => {
      if (!row || typeof row !== 'object') return null
      const parts: string[] = []
      for (const pk of pks) {
        const v = row[pk]
        if (v == null || String(v).trim() === '') return null
        parts.push(String(v).trim())
      }
      return parts.join('')
    }
    const nestedByKey = new Map<string, unknown>()
    for (const row of existingRows) {
      const k = keyOf(row)
      const nested = row && typeof row === 'object' ? (row as any).__subTables__ : null
      if (k && nested && typeof nested === 'object') nestedByKey.set(k, nested)
    }
    if (nestedByKey.size === 0) return nextRows
    return nextRows.map((row: any) => {
      if (!row || typeof row !== 'object') return row
      const k = keyOf(row)
      const existingNested = k ? (nestedByKey.get(k) as Record<string, unknown> | undefined) : undefined
      if (!existingNested) return row
      const own = (row.__subTables__ && typeof row.__subTables__ === 'object'
        ? row.__subTables__
        : {}) as Record<string, unknown>
      // 逐 key 比较，而不是「有 __subTables__ 就整体跳过」：重建出来的行往往**带着**一个
      // `__subTables__`，只是里面的那张表是空数组（binding.data 视角看不到嵌套行）。
      // 只有当本行该 key 确实为空、而被替换的切片里非空时，才把旧的补回来 —— 用户主动删空
      // 不会被这里复活，因为删空走的是嵌套写入路径，两边都会是空。
      const merged: Record<string, unknown> = { ...own }
      let changed = false
      for (const [key, val] of Object.entries(existingNested)) {
        const incoming = own[key]
        const incomingEmpty = !Array.isArray(incoming) || incoming.length === 0
        if (incomingEmpty && Array.isArray(val) && val.length > 0) {
          merged[key] = val
          changed = true
        }
      }
      return changed ? { ...row, __subTables__: merged } : row
    })
  }

  function patchFormDataSubTablesFromCurrentBindings() {
    // Use toRaw to bypass Vue's reactivity during bulk mutation, then trigger one update at the end.
    const rawFormData = toRaw(formData.value)
    if (!rawFormData.__subTables__ || typeof rawFormData.__subTables__ !== 'object') {
      rawFormData.__subTables__ = {}
    }
    const tbl = rawFormData.__subTables__ as Record<string, any>
    const myRowId = isMiSubTaskMode.value ? ctx.currentMiRowId.value : null
    const fullSnap = miFullSubTablesSnapshotRef.value
    for (const binding of subTableBindings.value) {
      const rawRows = toRaw(Array.isArray(binding.data) ? binding.data : [])
      let rows = cloneSubTableRows(rawRows)
      /**
       * MI participant-scoped slices are SHARED across all sub-tasks at the process level. {@code binding.data}
       * holds only the current participant after isolation, so writing it back verbatim would drop every other
       * participant's rows from the persisted slice (root cause of cross-participant data loss). Merge the other
       * participants' rows back from the pre-isolation snapshot; current participant's edits still win by row PK.
       */
      if (myRowId != null && fullSnap && isMiParticipantScopedSubTableBinding(binding)) {
        const snapRows = ctx.getSavedSubTableRows(fullSnap, binding) ?? []
        const otherParticipantRows = (Array.isArray(snapRows) ? snapRows : []).filter(
          (row: any) => !ctx.rowBelongsToCurrentMiScope(row, myRowId, binding),
        )
        if (otherParticipantRows.length > 0) {
          rows = cloneSubTableRows(
            mergeSubTableRowsByRowId(
              cloneSubTableRows(otherParticipantRows),
              rows,
              binding.primaryKeyFields ?? null,
            ),
          )
        }
      }
      if (fullSnap && isSharedAttachmentFileBinding(binding)) {
        const snapRows = ctx.getSavedSubTableRows(fullSnap, binding) ?? []
        if (Array.isArray(snapRows) && snapRows.length > 0) {
          rows = cloneSubTableRows(
            mergeSubTableRowsByRowId(
              cloneSubTableRows(snapRows),
              rows,
              binding.primaryKeyFields ?? null,
            ),
          )
        }
      }
      // `binding.data` is the GRID's view of this table and never carries a row's nested
      // `__subTables__`. A sub-table rendered inside another table's inline form (measured:
      // People nested in the Participants inline form) lives ONLY in the host row's nested slice —
      // its own binding stays empty. Rebuilding from binding.data alone therefore erased the row
      // the user had just added: [P4] Test-000001 → 1 nested People, [P5] → 0, and the submit
      // payload went out without it. Carry the nested payload over from the slice being replaced.
      rows = preserveNestedSubTablesFromExisting(
        ctx.getSavedSubTableRows(tbl, binding) ?? [],
        rows,
        binding.primaryKeyFields ?? null,
      )
      writeSubTableRows(tbl, binding, rows)
    }
    // Defer triggerRef to next macrotask — avoids synchronous watcher cascade during MI isolate.
    setTimeout(() => triggerRef(formData), 0)
  }

  function markBindingRowsNonReactive() {
    const markList = (bindings: typeof subTableBindings.value) => {
      for (const b of bindings) {
        if (!Array.isArray(b.data)) continue
        for (let i = 0; i < b.data.length; i++) {
          const row = b.data[i]
          if (row && typeof row === 'object') b.data[i] = markRaw(row)
        }
      }
    }
    markList(subTableBindings.value)
    for (const pf of previousForms.value) markList(pf.subTableBindings)
  }

  /** Completed-task detail: task header uses assigneeName from engine; sub-table rows may only have user id — align display for the assignee row. */
  function applyTaskAssigneeNameToMatchingSubTableRows(taskData: { assignee?: unknown; assigneeName?: unknown }) {
    const rawAid = taskData?.assignee
    const rawAname = taskData?.assigneeName
    const aid = extractUserIdFromCellValue(rawAid)
    if (!aid) return
    const displayName =
      typeof rawAname === 'string' || typeof rawAname === 'number'
        ? String(rawAname).trim()
        : unwrapUserLikeValueToDisplayString(rawAname)
    if (!displayName || displayName === '-') return
    const na = aid
    const apply = (bindings: typeof subTableBindings.value) => {
      for (const b of bindings) {
        const af = resolveAssigneeFieldForBinding(b as never)
        if (!af) continue
        for (const r of b.data || []) {
          if (!r || typeof r !== 'object') continue
          const rec = r as Record<string, unknown>
          if (extractUserIdFromCellValue(rec[af]) !== na) continue
          const dn = rec.assignee_display_name
          if (dn == null || String(dn).trim() === '') {
            rec.assignee_display_name = displayName
          }
        }
      }
    }
    apply(subTableBindings.value)
    for (const pf of previousForms.value) {
      apply(pf.subTableBindings)
    }
  }

  return {
    syncMainSubTableRows,
    patchFormDataSubTablesFromCurrentBindings,
    rebuildIsolatedSubTablesPayload,
    stripNestedFromAllTaskBindings,
    markBindingRowsNonReactive,
    applyTaskAssigneeNameToMatchingSubTableRows,
  }
}
