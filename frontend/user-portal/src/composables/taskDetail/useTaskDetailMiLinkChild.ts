import { processApi } from '@/api/process'
import { writeSubTableRows } from '@/composables/tasks/subTableStore'
import {
  ensureAutoPrimaryKeysForRows,
  repairMisassignedPrimaryKeyFromParentId,
  seedLinkChildForeignKeysFromParentRow,
  type AllocatePrimaryKeysFn,
} from '@/utils/subTableRowRuntime'
import {
  mergeSubTableRowsByRowId,
  findSubTableRowByMiExpansionId,
  findMiIsolatedParentRow,
  pullNestedRowsForBindingFromParentRows,
  collectSubTableSliceRowsForRelationTableId,
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  collapseMiLinkChildRowsToOnePerParticipant,
  backfillMiLinkChildPrimaryKeysFromVariables,
  repairMisassignedLinkChildStructuralFk,
  linkChildRowIsForeignParticipantPlaceholder,
  stripForeignParticipantIdIdwFromLinkChildRow,
  scopeMiLinkChildRowsForParentRow,
} from '@/composables/tasks/shared'
import {
  rowMatchesSubTablePrimaryKey,
  bindingMatchesMiSubTableName,
  type MiParticipantRowId,
} from '@/composables/tasks/miSubProcessScope'
import {
  cloneSubTableRows,
  subTableBindingMatches,
  collectSubTableBindingMatchKeys,
  subTableRowsLackSavedFieldPayload,
} from './subTableRowUtils'
import type { TaskDetailCtx } from './context'

export interface TaskDetailMiLinkChildFns {
  seedMiParticipantScopedBindingForeignKeys: (
    myRowId: MiParticipantRowId,
    options?: { allocateMissingPrimaryKeys?: boolean },
  ) => Promise<void>
  syncMiLinkChildRowsIntoParentNested: (
    childBinding: { bindingId: number; tableName: string },
    childRows: any[],
  ) => void
  hydrateMiLinkChildBindingsFromFullSnapshot: (myRowId: MiParticipantRowId) => void
}

export function createTaskDetailMiLinkChild(ctx: TaskDetailCtx): TaskDetailMiLinkChildFns {
  const {
    subTableBindings,
    previousForms,
    miSubProcessScope,
    miFullSubTablesSnapshotRef,
    lastBindingRelationTableMap,
    functionUnitIdRef,
  } = ctx

  function patchMiParentRowsWithNestedChildSlice(
    parentRows: any[],
    myRowId: MiParticipantRowId,
    childBinding: { bindingId: number; tableName: string },
    childSlice: any[],
  ): any[] {
    if (!Array.isArray(parentRows)) return parentRows
    const collectionPk = ctx.miCollectionPrimaryKeyFields()
    return parentRows.map(row => {
      if (!row || typeof row !== 'object') return row
      const rec = row as Record<string, unknown>
      if (
        !rowMatchesSubTablePrimaryKey(row, myRowId, collectionPk) &&
        !ctx.miRowBelongsToCurrentParticipant(row, myRowId, {
          tableName: childBinding.tableName,
          primaryKeyFields: collectionPk,
        })
      ) {
        return row
      }
      const scopedSlice = scopeMiLinkChildRowsForParentRow(rec, childSlice)
      const nest = {
        ...(rec.__subTables__ && typeof rec.__subTables__ === 'object'
          ? (rec.__subTables__ as Record<string, unknown>)
          : {})
      }
      const slice = cloneSubTableRows(scopedSlice)
      writeSubTableRows(nest, childBinding, slice)
      return { ...rec, __subTables__: nest }
    })
  }

  /** Prefer collection row {@code id_idw} (e.g. Test-000017) when seeding link-child FK {@code id}. */
  function resolveMiParticipantFkSeedValue(myRowId: MiParticipantRowId): MiParticipantRowId {
    const scopeName = miSubProcessScope.value?.subTableName ?? ''
    for (const b of subTableBindings.value) {
      if (!bindingMatchesMiSubTableName(b, scopeName)) continue
      const rows = Array.isArray(b.data) ? b.data : []
      const row =
        findSubTableRowByMiExpansionId(rows, myRowId)
        ?? findMiIsolatedParentRow(rows, myRowId)
      if (row && row.id_idw != null && row.id_idw !== '') {
        return row.id_idw as MiParticipantRowId
      }
    }
    return myRowId
  }

  /** Pull link-child rows nested under the current MI collection parent when top-level binding.data is still empty. */
  function materializeMiLinkChildBindingRowsFromParents(myRowId: MiParticipantRowId) {
    const scopeName = miSubProcessScope.value?.subTableName ?? ''
    const parentBinding = subTableBindings.value.find(b => bindingMatchesMiSubTableName(b, scopeName))
    if (!parentBinding) return
    const parentRow = findMiIsolatedParentRow(
      Array.isArray(parentBinding.data) ? parentBinding.data : [],
      myRowId,
    )
    if (!parentRow) return
    const peerMap = new Map<number, number | null>()
    for (const b of subTableBindings.value) {
      const tid = b.tableId != null ? Number(b.tableId) : null
      if (tid != null && Number.isFinite(tid)) peerMap.set(b.bindingId, tid)
    }
    for (const b of subTableBindings.value) {
      if (!isMiParticipantScopedSubTableBinding(b)) continue
      if (b.bindingId === parentBinding.bindingId) continue
      const existing = Array.isArray(b.data) ? b.data : []
      const nested = pullNestedRowsForBindingFromParentRows(
        {
          bindingId: b.bindingId,
          tableName: b.tableName ?? '',
          physicalTableName: b.physicalTableName,
          tableId: b.tableId ?? null,
        },
        [parentRow],
        peerMap,
      )
      if (nested.length === 0) continue
      if (existing.length === 0) {
        b.data = cloneSubTableRows(nested)
        continue
      }
      const fieldKeys = collectSubTableBindingMatchKeys(b as any)
      if (subTableRowsLackSavedFieldPayload(existing, fieldKeys)) {
        b.data = cloneSubTableRows(
          mergeSubTableRowsByRowId(existing, nested, b.primaryKeyFields ?? null),
        )
      }
    }
  }

  /**
   * Seed MI link-child FK (sub_task_id ← id_idw), repair misassigned row PK.
   * PK allocate is deferred to save/submit/complete — not on page load (avoids 100+ HTTP storm on empty link-form stubs).
   */
  async function seedMiParticipantScopedBindingForeignKeys(
    myRowId: MiParticipantRowId,
    options?: { allocateMissingPrimaryKeys?: boolean },
  ) {
    const shouldAllocate = options?.allocateMissingPrimaryKeys === true
    materializeMiLinkChildBindingRowsFromParents(myRowId)
    const fkSeed = resolveMiParticipantFkSeedValue(myRowId)
    const scopeName = miSubProcessScope.value?.subTableName ?? ''
    const parentBinding = subTableBindings.value.find(b => bindingMatchesMiSubTableName(b, scopeName))
    const parentRows = parentBinding && Array.isArray(parentBinding.data) ? parentBinding.data : []
    const parentRow =
      findSubTableRowByMiExpansionId(parentRows, myRowId)
      ?? findMiIsolatedParentRow(parentRows, myRowId)
    const parentParticipantRow: Record<string, unknown> =
      parentRow ?? ({ id_idw: fkSeed } as Record<string, unknown>)
    const parentTableId =
      parentBinding?.tableId != null && Number.isFinite(Number(parentBinding.tableId))
        ? Number(parentBinding.tableId)
        : null

    const fuId = functionUnitIdRef.value
    const allocateFn: AllocatePrimaryKeysFn | null =
      shouldAllocate && fuId && String(fuId).trim()
        ? async payload => {
            const res = await processApi.allocatePrimaryKeys(fuId, payload, ctx.taskId)
            const data = (res as { data?: { values?: string[] }; values?: string[] }).data ?? res
            return data?.values ?? []
          }
        : null

    for (const b of subTableBindings.value) {
      if (!isMiParticipantScopedSubTableBinding(b)) continue
      /** Sub task / MI collection rows — id_idw is the real PK; only link-child bindings (People) get repair/allocate. */
      if (bindingMatchesMiSubTableName(b, scopeName) || isMiDashboardSubTableBinding(b)) {
        continue
      }
      // An empty link-child binding means the user added no rows — never fabricate one. Save used to
      // insert `[{}]` here so the inline form-below-table strip had a row to bind its fields to, but
      // that strip is gone (rows are added/edited through the Link Form modal), so the placeholder had
      // no editor to fill it: seeding stamped it with the participant FK and allocation gave it a real
      // UUID, so every Save on an empty People table persisted one blank phantom row (#1531).
      const rowCount = Array.isArray(b.data) ? b.data.length : 0
      if (rowCount === 0) continue
      // Sibling participants' placeholder rows live in this same binding. Seeding/allocating them with the
      // CURRENT participant FK makes them falsely claim this participant; collapse then merges all into one
      // corrupt row (cross-participant id_idw leak). Only seed rows that belong to (or are fresh for) the
      // current participant; leave foreign placeholders byte-for-byte intact. (#1444)
      const foreignRows: Record<string, unknown>[] = []
      const seedableRows: Record<string, unknown>[] = []
      for (const raw of b.data) {
        if (!raw || typeof raw !== 'object') {
          foreignRows.push(raw as Record<string, unknown>)
          continue
        }
        const row = raw as Record<string, unknown>
        if (linkChildRowIsForeignParticipantPlaceholder(row, myRowId)) {
          foreignRows.push(row)
          continue
        }
        let next = seedLinkChildForeignKeysFromParentRow(
          row,
          b.fieldDefinitions,
          {
            bindingForeignKeyField: b.foreignKeyField,
            bindingLinkMode: b.bindingLinkMode,
            primaryKeyFields: b.primaryKeyFields,
            parentParticipantRow,
            parentTableId,
            legacyFkSeed: fkSeed,
          },
        )
        next = repairMisassignedPrimaryKeyFromParentId(next, b.fieldDefinitions, fkSeed)
        next = repairMisassignedLinkChildStructuralFk(next, fkSeed)
        next = stripForeignParticipantIdIdwFromLinkChildRow(next, myRowId)
        seedableRows.push(next)
      }
      let allocated = seedableRows
      if (allocateFn && b.tableId != null && b.fieldDefinitions?.length) {
        allocated = await ensureAutoPrimaryKeysForRows(
          b.fieldDefinitions,
          b.tableId,
          seedableRows,
          allocateFn,
          fuId,
        )
      }
      b.data = cloneSubTableRows(
        collapseMiLinkChildRowsToOnePerParticipant([...foreignRows, ...allocated]),
      )
      syncMiLinkChildRowsIntoParentNested(
        { bindingId: b.bindingId, tableName: b.tableName ?? '' },
        cloneSubTableRows(Array.isArray(b.data) ? b.data : []),
      )
    }
  }

  /** MI link-form child rows must live under {@code parentRow.__subTables__[childBindingId]} for reload / diagram, not only top-level slice. */
  function syncMiLinkChildRowsIntoParentNested(
    childBinding: { bindingId: number; tableName: string },
    childRows: any[]
  ) {
    const rid = ctx.currentMiRowId.value
    if (rid == null) return
    const myRowId = rid

    for (const parentBinding of subTableBindings.value) {
      if (parentBinding.bindingId === childBinding.bindingId) continue
      parentBinding.data = patchMiParentRowsWithNestedChildSlice(
        Array.isArray(parentBinding.data) ? parentBinding.data : [],
        myRowId,
        childBinding,
        childRows
      )
    }
  }

  /** Rebuild MI link-child bindings (People, …) solely from the flattened variables snapshot + previousForms. */
  function hydrateMiLinkChildBindingsFromFullSnapshot(myRowId: MiParticipantRowId) {
    const flat = miFullSubTablesSnapshotRef.value
    if (!flat) return
    const rtMap = lastBindingRelationTableMap.value
    ctx.refreshPreviousFormsSubTableDataFromSnapshot(flat)

    for (const binding of subTableBindings.value) {
      if (!isMiParticipantScopedSubTableBinding(binding)) continue
      const ownSlice = ctx.getSavedSubTableRows(flat, binding, false, rtMap) ?? []
      const candidates: any[] = []
      const tableIdRaw =
        binding.tableId != null ? Number(binding.tableId) : rtMap.get(binding.bindingId)
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
        candidates.push(...ownSlice)
      }
      for (const pf of previousForms.value) {
        for (const prev of pf.subTableBindings) {
          if (!subTableBindingMatches(binding, prev)) continue
          if (Array.isArray(prev.data)) candidates.push(...prev.data)
        }
      }
      const scoped = candidates
        .map(r => repairMisassignedLinkChildStructuralFk(r as Record<string, unknown>, myRowId))
        .filter(r => ctx.rowBelongsToCurrentMiScope(r, myRowId, binding))
      let merged = collapseMiLinkChildRowsToOnePerParticipant(scoped)
      const ownScoped = ownSlice
        .map(r => repairMisassignedLinkChildStructuralFk(r as Record<string, unknown>, myRowId))
        .filter(r => ctx.rowBelongsToCurrentMiScope(r, myRowId, binding))
      if (ownScoped.length > 0) {
        // Current task binding slice wins over stale sibling slices (e.g. prior userTask binding 63 vs 30).
        merged = mergeSubTableRowsByRowId(
          merged,
          collapseMiLinkChildRowsToOnePerParticipant(ownScoped),
          binding.primaryKeyFields,
        )
      }
      if (merged.length > 0) {
        const temp = { ...binding, data: merged }
        backfillMiLinkChildPrimaryKeysFromVariables([temp as typeof binding], flat, myRowId)
        merged = collapseMiLinkChildRowsToOnePerParticipant(temp.data)
        binding.data = cloneSubTableRows(merged)
      }
    }
  }

  return {
    seedMiParticipantScopedBindingForeignKeys,
    syncMiLinkChildRowsIntoParentNested,
    hydrateMiLinkChildBindingsFromFullSnapshot,
  }
}
