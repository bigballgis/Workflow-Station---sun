import type { FormField } from '@/components/FormRenderer.vue'
import {
  mergeSubTableRowsByRowId,
  mergeAllSubTableSlicesFromVariables,
  subTableVariablesIncludeMiRows,
  dropSubsumedSubTableRows,
  coerceSubTablesVariableToMap,
  collectSubTableSliceArraysDeep,
  cloneSubTableRows,
  applySharedAttachmentFinalizeAndMaterialize,
  isSharedAttachmentFileBinding,
  isFileOnlySubTableBinding,
  isMiParticipantScopedSubTableBinding,
  isMiDashboardSubTableBinding,
  filterRowsForMiParticipantSubTableBinding,
  filterRowsForSharedProcessSubTableBinding,
  filterRowsForMiCollectionSubTableBinding,
  collapseMiLinkChildRowsToOnePerParticipant,
  getSavedSubTableRows,
} from '@/composables/tasks/shared'
import {
  collectSubTableBindingMatchKeys,
  subTableRowsLackSavedFieldPayload,
  pickSubTableRowValueIgnoreKeyCase,
  getSavedSubTableRowsFromVariables,
  type SubTableBindingAlignable,
} from './subTableRowHelpers'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailMiHydrationFns {
  applySharedAttachmentHydrationToAllBindings: (
    topLevelValues?: Record<string, unknown> | null,
    flattened?: Record<string, unknown> | null,
  ) => void
  backfillSubTableBindingsFromVariables: (bindings: SubTableBindingAlignable[]) => void
  hydrateMiLinkChildBindingsForInitiatorMyRequest: () => void
  resyncMiDashboardFieldsFromVariablesOnBindings: (all: SubTableBindingAlignable[]) => void
  backfillEmptySubTableBindingsFromVariables: () => void
}

export function createApplicationDetailMiHydration(ctx: ApplicationDetailCtx): ApplicationDetailMiHydrationFns {
  const {
    formData,
    subTableBindings,
    previousForms,
    nodeFormMap,
    lastBindingRelationTableMap,
    isInitiatorMyRequestView,
  } = ctx

  function applySharedAttachmentHydrationToAllBindings(
    topLevelValues?: Record<string, unknown> | null,
    flattened?: Record<string, unknown> | null,
  ) {
    const tv = (topLevelValues ?? formData.value) as Record<string, unknown>
    const flat =
      flattened ??
      coerceSubTablesVariableToMap(formData.value.__subTables__)
    const opts = {
      flattened: flat,
      bindingTableById: lastBindingRelationTableMap.value,
    }
    applySharedAttachmentFinalizeAndMaterialize(subTableBindings.value, tv, opts)
    for (const pf of previousForms.value) {
      applySharedAttachmentFinalizeAndMaterialize(pf.subTableBindings, tv, opts)
    }
    for (const info of nodeFormMap.value.values()) {
      applySharedAttachmentFinalizeAndMaterialize(info.subTableBindings, tv, opts)
    }
  }

  function backfillSubTableBindingsFromVariables(bindings: SubTableBindingAlignable[]) {
    const savedMap = coerceSubTablesVariableToMap(formData.value.__subTables__)
    if (!savedMap || bindings.length === 0) return
    formData.value = { ...formData.value, __subTables__: savedMap }
    const sliceArrays = collectSubTableSliceArraysDeep(savedMap)

    for (const b of bindings) {
      if (isSharedAttachmentFileBinding(b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null })) {
        continue
      }
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

  /**
   * After hydrate/enrich passes, re-merge each binding from {@code __subTables__} so MI columns match
   * backend overlay (sub form2) — intermediate steps may have frozen stale task_current_node on row 0.
   */
  /**
   * My Request initiator: link-form child bindings (People / subtable2) often have a stale per-bindingId slice
   * (COMPLETED placeholders without age) while richer rows live under sibling __subTables__ keys (e.g. 30 vs 69).
   * Union all overlapping variable slices so Details modal can resolve payload by parent sub_task_id.
   */
  function hydrateMiLinkChildBindingsForInitiatorMyRequest() {
    if (!isInitiatorMyRequestView.value) return
    const savedMap = coerceSubTablesVariableToMap(formData.value.__subTables__)
    if (!savedMap) return
    const all = [
      ...subTableBindings.value,
      ...previousForms.value.flatMap(f => f.subTableBindings),
      ...Array.from(nodeFormMap.value.values()).flatMap(n => n.subTableBindings),
    ]
    for (const binding of all) {
      const hasLinkFormSchema = Array.isArray(binding.formFields) && binding.formFields.length > 0
      if (isMiDashboardSubTableBinding(binding)) continue
      if (!isMiParticipantScopedSubTableBinding(binding) && !hasLinkFormSchema) continue
      const fieldKeys = collectSubTableBindingMatchKeys(binding)
      if (fieldKeys.size === 0) continue
      const pk = binding.primaryKeyFields ?? null
      let merged: any[] = Array.isArray(binding.data) ? cloneSubTableRows(binding.data) : []
      const ownSlice = getSavedSubTableRows(savedMap as Record<string, any>, binding as any, false) ?? []
      merged = mergeSubTableRowsByRowId(merged, ownSlice, pk)
      const threshold =
        fieldKeys.size <= 2 ? 1 : Math.min(fieldKeys.size, Math.max(2, Math.ceil(fieldKeys.size * 0.25)))
      for (const val of Object.values(savedMap)) {
        if (!Array.isArray(val) || val.length === 0) continue
        const overlap = val.filter(row => {
          if (!row || typeof row !== 'object') return false
          let score = 0
          for (const k of fieldKeys) {
            const v = pickSubTableRowValueIgnoreKeyCase(row as Record<string, unknown>, k)
            if (v !== undefined && v !== null && v !== '') score++
          }
          return score >= threshold
        })
        if (overlap.length > 0) {
          merged = mergeSubTableRowsByRowId(merged, overlap, pk)
        }
      }
      if (merged.length > 0) {
        binding.data = dropSubsumedSubTableRows(
          collapseMiLinkChildRowsToOnePerParticipant(merged),
        )
      }
    }
  }

  function resyncMiDashboardFieldsFromVariablesOnBindings(all: SubTableBindingAlignable[]) {
    const savedSubTables = formData.value.__subTables__
    if (!savedSubTables || typeof savedSubTables !== 'object') return
    const savedMap = savedSubTables as Record<string, unknown>
    const useAllSlices = subTableVariablesIncludeMiRows(savedMap)
    const allSlicesMerged = useAllSlices
      ? mergeAllSubTableSlicesFromVariables(savedMap, undefined)
      : []
    for (const b of all) {
      const pk = b.primaryKeyFields ?? null
      const bindingSaved = getSavedSubTableRowsFromVariables(
        savedSubTables as Record<string, any>,
        {
          bindingId: Number(b.bindingId ?? 0),
          tableName: b.physicalTableName ?? b.tableName,
          tableDisplayName: b.tableName
        },
        pk
      )
      if (isSharedAttachmentFileBinding(b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null })) {
        continue
      }
      /** Initiator full case: MI collection rows come from this binding's slice only — never global allSlices (injects People / duplicate placeholders). */
      if (
        isInitiatorMyRequestView.value
        && isMiDashboardSubTableBinding(
          b as { columns?: Array<{ field?: string }> | null; tableName?: string },
        )
      ) {
        const fromOwnSlice = bindingSaved ?? []
        if (fromOwnSlice.length === 0 && !(Array.isArray(b.data) && b.data.length > 0)) continue
        b.data = dropSubsumedSubTableRows(
          filterRowsForMiCollectionSubTableBinding(
            mergeSubTableRowsByRowId(Array.isArray(b.data) ? b.data : [], fromOwnSlice, pk),
            b as { primaryKeyFields?: string[] | null; columns?: Array<{ field?: string }> | null },
          ),
        )
        continue
      }
      if (
        isMiParticipantScopedSubTableBinding(
          b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string },
        )
        && isInitiatorMyRequestView.value
      ) {
        continue
      }
      if (
        isMiParticipantScopedSubTableBinding(
          b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string },
        )
        && !isInitiatorMyRequestView.value
      ) {
        const fromOwnSlice = bindingSaved ?? []
        if (fromOwnSlice.length === 0 && !(Array.isArray(b.data) && b.data.length > 0)) continue
        b.data = dropSubsumedSubTableRows(
          filterRowsForMiParticipantSubTableBinding(
            mergeSubTableRowsByRowId(fromOwnSlice, Array.isArray(b.data) ? b.data : [], pk),
            b as { columns?: Array<{ field?: string }>; tableName?: string },
          ),
        )
        continue
      }
      // HMDC Attachment: file-only columns — global MI slice merge injects transaction rows as empty file rows.
      if (isFileOnlySubTableBinding(b as { columns?: Array<{ field?: string }> | null })) {
        const fromOwnSlice = bindingSaved ?? []
        if (fromOwnSlice.length === 0 && !(Array.isArray(b.data) && b.data.length > 0)) continue
        const merged = mergeSubTableRowsByRowId(
          Array.isArray(b.data) ? b.data : [],
          fromOwnSlice,
          pk,
        )
        b.data = dropSubsumedSubTableRows(
          filterRowsForSharedProcessSubTableBinding(
            merged,
            b as {
              columns?: Array<{ field?: string }> | null
              foreignKeyField?: string | null
              tableName?: string
              physicalTableName?: string
              tableId?: number | null
            },
          ),
        )
        continue
      }
      const fromVariables = useAllSlices
        ? mergeSubTableRowsByRowId(allSlicesMerged, bindingSaved ?? [], pk)
        : (bindingSaved ?? [])
      if (fromVariables.length === 0 && !(Array.isArray(b.data) && b.data.length > 0)) continue
      // Variables (backend MI overlay) win over binding rows polluted by enrich.
      b.data = dropSubsumedSubTableRows(
        mergeSubTableRowsByRowId(Array.isArray(b.data) ? b.data : [], fromVariables, pk)
      )
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
      if (isSharedAttachmentFileBinding(b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null })) {
        continue
      }
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

  return {
    applySharedAttachmentHydrationToAllBindings,
    backfillSubTableBindingsFromVariables,
    hydrateMiLinkChildBindingsForInitiatorMyRequest,
    resyncMiDashboardFieldsFromVariablesOnBindings,
    backfillEmptySubTableBindingsFromVariables,
  }
}
