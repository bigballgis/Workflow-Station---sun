import type { FormField } from '@/components/FormRenderer.vue'
import type { CompletedTaskFormData } from '@/api/processForm'
import {
  mergeSubTableRowsByRowId,
  coerceSubTablesVariableToMap,
  collectSubTableSliceArraysDeep,
  mergeAllSlicesForSharedProcessSubTableBinding,
  resolveSubTableRowsForBinding,
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  finalizeSharedProcessSubTableBindingRows,
  applySharedAttachmentFinalizeAndMaterialize,
  isSharedAttachmentFileBinding,
  finalizeMiCollectionSubTableBindingRows,
  mergeMiCollectionSubTableRows,
} from '@/composables/tasks/shared'
import {
  cloneSubTableRows,
  cloneAndFlattenSubTablesMap,
  bindingIdsPreferStrictSubTableLookup,
  subTableBindingMatches,
  collectSubTableBindingMatchKeys,
  subTableRowsLackSavedFieldPayload,
} from './subTableRowUtils'
import type { TaskDetailState } from './useTaskDetailState'
import type { TaskDetailCtx } from './context'

export interface TaskDetailHydrationFns {
  getSavedSubTableRows: (
    savedSubTables: any,
    binding: {
      bindingId: number
      tableName: string
      physicalTableName?: string
      tableId?: number | null
      primaryKeyFields?: string[] | null
      columns?: Array<{ field?: string }> | null
    },
    forbidNameFallback?: boolean,
  ) => any[] | undefined
  applySavedRowsToBindings: <T extends Array<{ bindingId: number; tableName: string; data: any[] }>>(
    bindings: T,
    savedSubTables: any,
  ) => T
  applyCompletedSnapshotToForm: (data: CompletedTaskFormData | null) => void
  refreshPreviousFormsSubTableDataFromSnapshot: (flat: Record<string, unknown> | null) => void
  hydrateCurrentSubTablesFromPreviousForms: () => void
  backfillEmptySubTableBindingsFromVariables: () => void
  rehydrateSharedAttachmentBindings: (
    bindings: TaskDetailState['subTableBindings']['value'],
    topLevelValues: Record<string, unknown> | null | undefined,
    flattened?: Record<string, unknown> | null,
  ) => void
  rehydrateSharedProcessSubTableBindings: (
    savedSubTablesSource?: Record<string, unknown> | null,
    preFlattened?: Record<string, unknown>,
  ) => void
}

export function createTaskDetailSubTableHydration(ctx: TaskDetailCtx): TaskDetailHydrationFns {
  const {
    subTableBindings,
    previousForms,
    nodeFormMap,
    lastBindingRelationTableMap,
    isMiSubTaskMode,
  } = ctx
  const { formData } = ctx.taskForm

  /**
   * `forbidNameFallback` param kept for call-site source compatibility (many callers pass
   * `ambiguous.has(binding.bindingId)` positionally) but is now UNUSED — resolveSubTableRowsForBinding
   * no longer has a table-name string-key fallback to forbid.
   */
  function getSavedSubTableRows(
    savedSubTables: any,
    binding: {
      bindingId: number
      tableName: string
      physicalTableName?: string
      tableId?: number | null
      primaryKeyFields?: string[] | null
      columns?: Array<{ field?: string }> | null
    },
    _forbidNameFallback = false,
  ): any[] | undefined {
    return resolveSubTableRowsForBinding(savedSubTables, binding, {
      bindingTableById: lastBindingRelationTableMap.value,
      mergeSiblingSlices:
        isMiDashboardSubTableBinding(binding) && !isMiSubTaskMode.value,
    })
  }

  function applySavedRowsToBindings<T extends Array<{ bindingId: number; tableName: string; data: any[] }>>(bindings: T, savedSubTables: any): T {
    if (!savedSubTables || typeof savedSubTables !== 'object') return bindings
    const ambiguous = bindingIdsPreferStrictSubTableLookup(bindings)
    bindings.forEach(binding => {
      const saved = getSavedSubTableRows(savedSubTables, binding, ambiguous.has(binding.bindingId))
      if (saved) {
        binding.data = cloneSubTableRows(
          isMiDashboardSubTableBinding(binding)
            ? finalizeMiCollectionSubTableBindingRows(saved, binding)
            : saved,
        )
      }
    })
    return bindings
  }

  function applyCompletedSnapshotToForm(data: CompletedTaskFormData | null) {
    const snapshotValues = (data?.snapshot?.fieldValues || {}) as Record<string, any>
    formData.value = { ...snapshotValues }

    const savedSubTables = snapshotValues.__subTables__
    applySavedRowsToBindings(subTableBindings.value, savedSubTables)
    previousForms.value.forEach(form => {
      applySavedRowsToBindings(form.subTableBindings, savedSubTables)
    })

    if (savedSubTables && typeof savedSubTables === 'object' && nodeFormMap.value.size > 0) {
      const nextMap = new Map(nodeFormMap.value)
      nextMap.forEach(info => {
        info.values = { ...snapshotValues }
        applySavedRowsToBindings(info.subTableBindings, savedSubTables)
      })
      nodeFormMap.value = nextMap
    }

    ctx.alignProcessSubTableBindingsBySharedTable()
  }

  /** Re-load previousForms slices from the flattened MI snapshot (sub form1 attachment often nested-only). */
  function refreshPreviousFormsSubTableDataFromSnapshot(flat: Record<string, unknown> | null) {
    if (!flat) return
    for (const pf of previousForms.value) {
      const ambiguous = bindingIdsPreferStrictSubTableLookup(pf.subTableBindings as any[])
      for (const binding of pf.subTableBindings) {
        const saved = getSavedSubTableRows(flat, binding, ambiguous.has(binding.bindingId))
        if (Array.isArray(saved) && saved.length > 0) {
          binding.data = cloneSubTableRows(
            isMiDashboardSubTableBinding(binding)
              ? finalizeMiCollectionSubTableBindingRows(saved, binding)
              : saved,
          )
        }
        if (isSharedAttachmentFileBinding(binding)) {
          applySharedAttachmentFinalizeAndMaterialize([binding as typeof subTableBindings.value[0]], null, {
            flattened: flat,
            bindingTableById: lastBindingRelationTableMap.value,
          })
        }
      }
    }
  }

  function hydrateCurrentSubTablesFromPreviousForms() {
    for (const current of subTableBindings.value) {
      // MI collection/dashboard bindings (e.g. Participants): every sibling BPMN node's
      // `previousForms` snapshot duplicates the SAME logical row under its OWN bindingId, so
      // `subTableBindingMatches` (tableName/tableId only, not participant-aware) treats them as
      // interchangeable. The current node's own row for THIS participant can legitimately be
      // "thin" (still awaiting `resyncMiParticipantSubTablesFromVariables`'s scoped hydrate) —
      // that thinness must not be read as "lacks payload, backfill from a sibling", or a stale
      // sibling's copy of this SAME participant's row silently wins the merge (no field on our
      // own thin row to overwrite it with). Skip entirely; the later MI-aware resync owns this.
      if (isMiDashboardSubTableBinding(current)) continue
      const prevMatches = previousForms.value
        .flatMap(form => form.subTableBindings)
        .filter(binding =>
          binding.data?.length > 0 &&
          subTableBindingMatches(current, binding)
        )
      if (prevMatches.length === 0) continue

      const fieldKeys = collectSubTableBindingMatchKeys(current as any)
      const lacksPayload =
        !Array.isArray(current.data) ||
        current.data.length === 0 ||
        subTableRowsLackSavedFieldPayload(current.data, fieldKeys)

      if (!lacksPayload) continue

      const pk = current.primaryKeyFields ?? null
      let merged: any[] = Array.isArray(current.data) ? cloneSubTableRows(current.data) : []
      for (const prev of prevMatches) {
        merged = mergeSubTableRowsByRowId(merged, prev.data, pk)
      }
      if (merged.length > 0) {
        current.data = cloneSubTableRows(merged)
      }
    }
  }

  function backfillEmptySubTableBindingsFromVariables() {
    const savedMap = coerceSubTablesVariableToMap(formData.value.__subTables__)
    if (!savedMap) return
    formData.value = { ...formData.value, __subTables__: savedMap }
    const sliceArrays = collectSubTableSliceArraysDeep(savedMap)

    const all = [
      ...subTableBindings.value,
      ...previousForms.value.flatMap(f => f.subTableBindings),
      ...Array.from(nodeFormMap.value.values()).flatMap(n => n.subTableBindings)
    ]

    for (const b of all) {
      const fieldKeys = collectSubTableBindingMatchKeys(b as { columns?: Array<{ field?: string }>; formFields?: FormField[] })
      if (fieldKeys.size === 0) continue

      if (
        Array.isArray(b.data) &&
        b.data.length > 0 &&
        !subTableRowsLackSavedFieldPayload(b.data, fieldKeys)
      ) {
        continue
      }

      let best: any[] | null = null
      let bestScore = 0
      for (const val of sliceArrays) {
        if (!Array.isArray(val) || val.length === 0) continue
        const row0 = val[0]
        if (!row0 || typeof row0 !== 'object') continue
        const row0KeysLower = new Set(Object.keys(row0 as object).map(k => k.toLowerCase()))
        let score = 0
        for (const k of fieldKeys) {
          if (row0KeysLower.has(k.toLowerCase())) score++
        }
        const threshold =
          fieldKeys.size <= 2 ? 1 : Math.min(fieldKeys.size, Math.max(2, Math.ceil(fieldKeys.size * 0.25)))
        if (score >= threshold && score > bestScore) {
          bestScore = score
          best = val as any[]
        }
      }
      if (best) {
        b.data = best.map((r: any) => (r && typeof r === 'object' ? { ...r } : r))
      }
    }
  }

  function rehydrateSharedAttachmentBindings(
    bindings: typeof subTableBindings.value,
    topLevelValues: Record<string, unknown> | null | undefined,
    flattened?: Record<string, unknown> | null,
  ) {
    applySharedAttachmentFinalizeAndMaterialize(bindings, topLevelValues, {
      flattened: flattened ?? coerceSubTablesVariableToMap(formData.value.__subTables__),
      bindingTableById: lastBindingRelationTableMap.value,
    })
  }

  function rehydrateSharedProcessSubTableBindings(
    savedSubTablesSource?: Record<string, unknown> | null,
    /** Pre-flattened copy to avoid redundant deep-clone + flatten (perf). */
    preFlattened?: Record<string, unknown>,
  ) {
    const savedMap = coerceSubTablesVariableToMap(
      savedSubTablesSource ?? formData.value.__subTables__,
    )

    if (!savedMap) {
      ctx.patchFormDataSubTablesFromCurrentBindings()
      return
    }

    const flattened = preFlattened ?? cloneAndFlattenSubTablesMap(savedMap)
    const rtMap = lastBindingRelationTableMap.value

    const applyTo = (bindings: typeof subTableBindings.value) => {
      for (const binding of bindings) {
        if (isMiDashboardSubTableBinding(binding)) {
          const resolved =
            resolveSubTableRowsForBinding(flattened, binding, {
              bindingTableById: rtMap,
              mergeSiblingSlices: !isMiSubTaskMode.value,
            }) ?? []
          const existing = Array.isArray(binding.data) ? binding.data : []
          if (isMiSubTaskMode.value) {
            /** MI sub-task: own slice only — global allSlices merge injects attachment / ghost rows. */
            binding.data = mergeMiCollectionSubTableRows([existing, resolved], binding)
          } else {
            const merged = mergeAllSlicesForSharedProcessSubTableBinding(flattened, binding, rtMap)
            binding.data = mergeMiCollectionSubTableRows([resolved, merged, existing], binding)
          }
          continue
        }
        if (isMiParticipantScopedSubTableBinding(binding)) continue
        const merged = mergeAllSlicesForSharedProcessSubTableBinding(flattened, binding, rtMap)
        const existing = Array.isArray(binding.data) ? binding.data : []
        const combined =
          merged.length > 0
            ? mergeSubTableRowsByRowId(existing, merged, binding.primaryKeyFields ?? null)
            : existing
        binding.data = finalizeSharedProcessSubTableBindingRows(combined, binding)
      }
    }

    applyTo(subTableBindings.value)
    for (const pf of previousForms.value) {
      applyTo(pf.subTableBindings)
    }
    for (const info of nodeFormMap.value.values()) {
      rehydrateSharedAttachmentBindings(
        info.subTableBindings,
        formData.value as Record<string, unknown>,
        savedMap,
      )
    }
    ctx.patchFormDataSubTablesFromCurrentBindings()
  }

  return {
    getSavedSubTableRows,
    applySavedRowsToBindings,
    applyCompletedSnapshotToForm,
    refreshPreviousFormsSubTableDataFromSnapshot,
    hydrateCurrentSubTablesFromPreviousForms,
    backfillEmptySubTableBindingsFromVariables,
    rehydrateSharedAttachmentBindings,
    rehydrateSharedProcessSubTableBindings,
  }
}
