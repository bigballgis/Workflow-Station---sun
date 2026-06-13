import { toRaw, triggerRef, markRaw } from 'vue'
import {
  mergeSubTableRowsByRowId,
  stripNestedSubTablesFromRows,
  isMiParticipantScopedSubTableBinding,
  isSharedAttachmentFileBinding,
} from '@/composables/tasks/shared'
import {
  resolveAssigneeFieldForBinding,
} from '@/utils/subTableAssignment'
import {
  unwrapUserLikeValueToDisplayString,
  extractUserIdFromCellValue,
} from '@/components/subTableAddDialogHelpers'
import {
  cloneSubTableRows,
  bindingIdsPreferStrictSubTableLookup,
  subTableBindingMatches,
  normalizeSubTableName,
} from './subTableRowUtils'
import type { TaskDetailCtx } from './context'

export interface TaskDetailSyncFns {
  syncMainSubTableRows: (bindingId: number, rows: any[]) => void
  patchFormDataSubTablesFromCurrentBindings: () => void
  rebuildIsolatedSubTablesPayload: () => Record<string, any>
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

  /** After MI isolation, __subTables__ must carry all participants again; current binding only has this MI row. */
  function rebuildIsolatedSubTablesPayload(): Record<string, any> {
    const subTables: Record<string, any> = {}
    const ingest = (bindings: typeof subTableBindings.value) => {
      const collision = bindingIdsPreferStrictSubTableLookup(bindings)
      for (const binding of bindings) {
        const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
        const canonical = String(binding.bindingId)
        const prev = ctx.getSavedSubTableRows(subTables, binding, collision.has(binding.bindingId))
        const merged = mergeSubTableRowsByRowId(prev, rows, binding.primaryKeyFields)
        const out = cloneSubTableRows(merged)
        subTables[binding.bindingId] = out
        subTables[canonical] = out
        if (binding.tableName) {
          subTables[binding.tableName] = out
          subTables[normalizeSubTableName(binding.tableName)] = out
        }
      }
    }
    for (const pf of previousForms.value) {
      ingest(pf.subTableBindings)
    }
    ingest(subTableBindings.value)
    return subTables
  }

  function syncMainSubTableRows(bindingId: number, rows: any[]) {
    const source = subTableBindings.value.find(b => b.bindingId === bindingId)
    if (!source) return

    const nextRows = Array.isArray(rows) ? rows : []

    const subTables = { ...((formData.value.__subTables__ as Record<string, any>) || {}) }
    const strictSlices = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)
    const existing = ctx.getSavedSubTableRows(subTables, source, strictSlices.has(source.bindingId))
    const merged = isMiSubTaskMode.value
      ? mergeSubTableRowsByRowId(existing, nextRows, source.primaryKeyFields)
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

    subTables[source.bindingId] = out
    subTables[String(source.bindingId)] = out
    if (source.tableName) {
      subTables[source.tableName] = out
      subTables[normalizeSubTableName(source.tableName)] = out
    }

    if (isMiSubTaskMode.value) {
      ctx.syncMiLinkChildRowsIntoParentNested(
        { bindingId: source.bindingId, tableName: source.tableName },
        out
      )
      const ambiguous = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)
      for (const parentBinding of subTableBindings.value) {
        if (parentBinding.bindingId === source.bindingId) continue
        const parentRows = cloneSubTableRows(
          Array.isArray(parentBinding.data) ? parentBinding.data : []
        )
        subTables[parentBinding.bindingId] = parentRows
        subTables[String(parentBinding.bindingId)] = parentRows
        if (parentBinding.tableName) {
          subTables[parentBinding.tableName] = parentRows
          subTables[normalizeSubTableName(parentBinding.tableName)] = parentRows
        }
      }
    }

    formData.value = { ...formData.value, __subTables__: subTables }
    scheduleSubTableAutosave()
  }

  /** After MI refilter, align {@link formData}.__subTables__ keys for current bindings so autosave/submit matches the grid. */
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
      tbl[binding.bindingId] = rows
      tbl[String(binding.bindingId)] = rows
      if (binding.tableName) {
        tbl[binding.tableName] = rows
        tbl[normalizeSubTableName(binding.tableName)] = rows
      }
      const phys = binding.physicalTableName
      if (phys) {
        tbl[phys] = rows
        tbl[normalizeSubTableName(phys)] = rows
      }
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
        const af = resolveAssigneeFieldForBinding(b.columns, b.tableName)
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
