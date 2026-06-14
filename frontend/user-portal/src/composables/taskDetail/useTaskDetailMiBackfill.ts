import {
  isMiParticipantScopedSubTableBinding,
  collectSubTableSliceRowsForRelationTableId,
  collapseMiLinkChildRowsToOnePerParticipant,
  backfillMiLinkChildPrimaryKeysFromVariables,
  repairMisassignedLinkChildStructuralFk,
  filterRowsForMiParticipantSubTableBinding,
  finalizeMiCollectionSubTableBindingRows,
} from '@/composables/tasks/shared'
import {
  rowMatchesSubTablePrimaryKey,
  bindingMatchesMiSubTableName,
  filterBindingsToMiParticipantRow,
  hasConfiguredPrimaryKeyFields,
  type MiParticipantRowId,
} from '@/composables/tasks/miSubProcessScope'
import {
  cloneSubTableRows,
  collectSubTableBindingMatchKeys,
  subTableRowsLackSavedFieldPayload,
} from './subTableRowUtils'
import type { TaskDetailState } from './useTaskDetailState'
import type { TaskDetailCtx } from './context'

export interface TaskDetailMiBackfillFns {
  miSubTaskSubTablesLoadSource: () => Record<string, unknown> | null
  scopeMiSubTaskBindingsToCurrentParticipant: (
    bindings: TaskDetailState['subTableBindings']['value'],
    myRowId: MiParticipantRowId | null | undefined,
  ) => void
  backfillMiParticipantScopedBindingsFromSnapshot: (
    bindings: TaskDetailState['subTableBindings']['value'],
    myRowId: MiParticipantRowId,
  ) => void
  forceSeedMiCollectionBindingForCurrentParticipant: () => void
  reScopeMiSubTaskParticipantBindings: () => void
}

export function createTaskDetailMiBackfill(ctx: TaskDetailCtx): TaskDetailMiBackfillFns {
  const {
    subTableBindings,
    previousForms,
    miSubProcessScope,
    miFullSubTablesSnapshotRef,
    lastBindingRelationTableMap,
    isMiSubTaskMode,
  } = ctx
  const { formData } = ctx.taskForm

  /** MI sub-task Save/display must read the full process snapshot — not the post-isolate truncated {@code formData.__subTables__}. */
  function miSubTaskSubTablesLoadSource(): Record<string, unknown> | null {
    if (isMiSubTaskMode.value && miFullSubTablesSnapshotRef.value) {
      return miFullSubTablesSnapshotRef.value
    }
    const saved = formData.value.__subTables__
    return saved && typeof saved === 'object' ? (saved as Record<string, unknown>) : null
  }

  /** MI sub-task To Do: Sub Task grid + related bindings scoped to {@link currentMiRowId} only. */
  function scopeMiSubTaskBindingsToCurrentParticipant(
    bindings: typeof subTableBindings.value,
    myRowId: MiParticipantRowId | null | undefined,
  ) {
    if (!isMiSubTaskMode.value || myRowId == null) return
    ctx.sanitizeMiCollectionBindingsData(bindings)
    const scope = miSubProcessScope.value
    if (!scope?.subTableName) return
    const collectionBinding = ctx.resolveMiCollectionBindingAcrossTaskForms()
    const participantPk =
      collectionBinding?.primaryKeyFields ?? ctx.resolveMiCollectionParticipantPkFields()
    if (
      collectionBinding
      && !hasConfiguredPrimaryKeyFields(participantPk)
    ) {
      ctx.warnMiMissingPrimaryKey(collectionBinding)
      return
    }
    filterBindingsToMiParticipantRow(bindings as typeof subTableBindings.value, scope, myRowId, {
      participantPrimaryKeyFields: participantPk,
      collectionTableId:
        collectionBinding?.tableId != null && Number.isFinite(Number(collectionBinding.tableId))
          ? Number(collectionBinding.tableId)
          : null,
    })
    backfillEmptyMiCollectionBindingsFromSnapshot(
      bindings,
      myRowId,
      participantPk,
      collectionBinding?.tableId ?? null,
    )
    backfillMiParticipantScopedBindingsFromSnapshot(bindings, myRowId)
  }

  /** People / link-child: merge prior-step business fields from the full MI snapshot when binding.data is empty or FK-only. */
  function backfillMiParticipantScopedBindingsFromSnapshot(
    bindings: typeof subTableBindings.value,
    myRowId: MiParticipantRowId,
  ) {
    const flat = miFullSubTablesSnapshotRef.value
    if (!flat) return
    const rtMap = lastBindingRelationTableMap.value

    for (const binding of bindings) {
      if (!isMiParticipantScopedSubTableBinding(binding)) continue
      const existing = Array.isArray(binding.data) ? binding.data : []
      const fieldKeys = collectSubTableBindingMatchKeys(binding as any)
      const lacksPayload =
        existing.length === 0 || subTableRowsLackSavedFieldPayload(existing, fieldKeys)

      if (!lacksPayload) continue

      const candidates: any[] = cloneSubTableRows(existing)
      const tableIdRaw =
        binding.tableId != null && Number.isFinite(Number(binding.tableId))
          ? Number(binding.tableId)
          : rtMap.get(binding.bindingId)
      if (Number.isFinite(Number(tableIdRaw))) {
        candidates.push(
          ...collectSubTableSliceRowsForRelationTableId(
            flat,
            Number(tableIdRaw),
            rtMap,
            binding.tableName,
            binding.physicalTableName,
          ),
        )
      } else {
        const own = ctx.getSavedSubTableRows(flat, binding) ?? []
        candidates.push(...own)
      }
      const scoped = candidates
        .map(r => repairMisassignedLinkChildStructuralFk(r as Record<string, unknown>, myRowId))
        .filter(r => ctx.rowBelongsToCurrentMiScope(r, myRowId, binding))
      let merged = collapseMiLinkChildRowsToOnePerParticipant(scoped)
      if (merged.length > 0) {
        const temp = { ...binding, data: merged } as typeof binding
        backfillMiLinkChildPrimaryKeysFromVariables([temp], flat, myRowId)
        merged = collapseMiLinkChildRowsToOnePerParticipant(temp.data)
        binding.data = cloneSubTableRows(
          filterRowsForMiParticipantSubTableBinding(merged, binding),
        )
      }
    }
  }

  /** When layout-sync adds a collection binding with an empty slice, seed the current participant row from the full snapshot. */
  function backfillEmptyMiCollectionBindingsFromSnapshot(
    bindings: typeof subTableBindings.value,
    myRowId: MiParticipantRowId,
    participantPk: string[] | null | undefined,
    collectionTableId: number | null | undefined,
  ) {
    const scope = miSubProcessScope.value
    if (!scope?.subTableName || !hasConfiguredPrimaryKeyFields(participantPk)) return
    const flat = miFullSubTablesSnapshotRef.value
    if (!flat) return
    const rtMap = lastBindingRelationTableMap.value
    const tid =
      collectionTableId != null && Number.isFinite(Number(collectionTableId))
        ? Number(collectionTableId)
        : null

    for (const binding of bindings) {
      const isCollection =
        ctx.isCurrentMiCollectionSubTableBinding(binding)
        || bindingMatchesMiSubTableName(binding, scope.subTableName)
        || (tid != null && binding.tableId != null && Number(binding.tableId) === tid)
      if (!isCollection) continue
      if (Array.isArray(binding.data) && binding.data.length > 0) continue

      let candidates: any[] = []
      const bindingTid =
        binding.tableId != null && Number.isFinite(Number(binding.tableId))
          ? Number(binding.tableId)
          : rtMap.get(binding.bindingId) ?? tid
      if (bindingTid != null && Number.isFinite(Number(bindingTid))) {
        candidates = collectSubTableSliceRowsForRelationTableId(
          flat,
          Number(bindingTid),
          rtMap,
          binding.tableName,
          binding.physicalTableName,
        )
      } else {
        candidates = ctx.getSavedSubTableRows(flat, binding) ?? []
      }
      const hit = candidates.filter(row =>
        rowMatchesSubTablePrimaryKey(row, myRowId, participantPk),
      )
      if (hit.length > 0) {
        binding.data = cloneSubTableRows(finalizeMiCollectionSubTableBindingRows(hit, binding))
      }
    }
  }

  /** Last-resort: ensure the MI collection grid has the current participant row from the full snapshot. */
  function forceSeedMiCollectionBindingForCurrentParticipant() {
    if (!isMiSubTaskMode.value || ctx.currentMiRowId.value == null) return
    const flat = miFullSubTablesSnapshotRef.value
    if (!flat) return
    const myRowId = ctx.currentMiRowId.value
    const participantPk = ctx.resolveMiCollectionParticipantPkFields()
    if (!hasConfiguredPrimaryKeyFields(participantPk)) return
    const rtMap = lastBindingRelationTableMap.value

    for (const binding of subTableBindings.value) {
      if (!ctx.isCurrentMiCollectionSubTableBinding(binding)) continue
      const existing = Array.isArray(binding.data) ? binding.data : []
      if (existing.some(row => rowMatchesSubTablePrimaryKey(row, myRowId, participantPk))) continue

      let candidates: any[] = []
      const tableIdRaw =
        binding.tableId != null && Number.isFinite(Number(binding.tableId))
          ? Number(binding.tableId)
          : rtMap.get(binding.bindingId)
      if (Number.isFinite(Number(tableIdRaw))) {
        candidates = collectSubTableSliceRowsForRelationTableId(
          flat,
          Number(tableIdRaw),
          rtMap,
          binding.tableName,
          binding.physicalTableName,
        )
      }
      if (candidates.length === 0) {
        candidates = ctx.getSavedSubTableRows(flat, binding) ?? []
      }
      const hit = candidates.filter(row => rowMatchesSubTablePrimaryKey(row, myRowId, participantPk))
      if (hit.length > 0) {
        binding.data = cloneSubTableRows(finalizeMiCollectionSubTableBindingRows(hit, binding))
      }
    }

    backfillMiParticipantScopedBindingsFromSnapshot(subTableBindings.value, myRowId)
    ctx.hydrateMiLinkChildBindingsFromFullSnapshot(myRowId)
  }

  function reScopeMiSubTaskParticipantBindings() {
    const myRowId = ctx.currentMiRowId.value
    if (!isMiSubTaskMode.value || myRowId == null) return
    scopeMiSubTaskBindingsToCurrentParticipant(subTableBindings.value, myRowId)
    for (const pf of previousForms.value) {
      scopeMiSubTaskBindingsToCurrentParticipant(
        pf.subTableBindings as typeof subTableBindings.value,
        myRowId,
      )
    }
  }

  return {
    miSubTaskSubTablesLoadSource,
    scopeMiSubTaskBindingsToCurrentParticipant,
    backfillMiParticipantScopedBindingsFromSnapshot,
    forceSeedMiCollectionBindingForCurrentParticipant,
    reScopeMiSubTaskParticipantBindings,
  }
}
