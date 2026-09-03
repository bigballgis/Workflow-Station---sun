import type { FormField } from '@/components/FormRenderer.vue'
import {
  mergeSubTableRowsByRowId,
  mergeAllSubTableSlicesFromVariables,
  subTableVariablesIncludeMiRows,
  dropSubsumedSubTableRows,
  coerceSubTablesVariableToMap,
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
  rowIsSelfOwnedByStructuralFk,
  miChildFkConfigOfBinding,
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

/**
 * Resolve saved rows for a binding that has no scoped slice of its own (e.g. a My Request REQUEST
 * scene binding — it never writes __subTables__ under its own bindingId) by finding another
 * binding sharing the SAME table_id whose slice IS present.
 *
 * This binding's own numeric __subTables__ key is always absent here (the "has no scoped data"
 * check upstream already ruled that out), so this only needs to walk `savedMap`'s OTHER numeric
 * keys and keep the ones `bindingTableById` maps to the same table_id — never a field-name/overlap
 * guess across the whole process. Scanning by field-shape similarity previously let a completely
 * unrelated relation table (different table_id, coincidentally same column names like `name` /
 * `sub_task_id`) get selected as if it were this binding's own data.
 *
 * Among same-table_id candidates, prefer whichever carries a structural self-reference FK
 * (sub_task_id / participant_id / … === its own PK) — that's only ever stamped by the binding whose
 * form actually owns the row's writes; a binding that only holds an initialization-time copy never
 * gets it. Falls back to the richest same-table_id candidate when none is self-owned.
 */
function resolveSameTableIdSliceForBinding(
  binding: { bindingId?: number; tableId?: number | null; primaryKeyFields?: string[] },
  savedMap: Record<string, unknown>,
  bindingTableById: Map<number, number | null>,
): any[] | null {
  const selfTid =
    binding.tableId != null && Number.isFinite(Number(binding.tableId))
      ? Number(binding.tableId)
      : (binding.bindingId != null ? bindingTableById.get(Number(binding.bindingId)) ?? null : null)
  if (selfTid == null || !Number.isFinite(selfTid)) return null

  let best: any[] | null = null
  let bestIsSelfOwned = false
  let bestLength = 0
  for (const [key, val] of Object.entries(savedMap)) {
    const kid = Number(key)
    if (!Number.isFinite(kid) || kid === Number(binding.bindingId)) continue
    if (!Array.isArray(val) || val.length === 0) continue
    const otherTid = bindingTableById.get(kid)
    if (otherTid == null || Number(otherTid) !== selfTid) continue
    const isSelfOwned = val.some(
      r => r && typeof r === 'object' && rowIsSelfOwnedByStructuralFk(r as Record<string, unknown>, binding.primaryKeyFields ?? null, miChildFkConfigOfBinding(binding as any)),
    )
    const better =
      (isSelfOwned && !bestIsSelfOwned) ||
      (isSelfOwned === bestIsSelfOwned && val.length > bestLength)
    if (better) {
      best = val as any[]
      bestIsSelfOwned = isSelfOwned
      bestLength = val.length
    }
  }
  return best
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

    for (const b of bindings) {
      if (isSharedAttachmentFileBinding(b)) {
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

      const found = resolveSameTableIdSliceForBinding(b, savedMap, lastBindingRelationTableMap.value)
      if (found) {
        b.data = found.map((r: any) => (r && typeof r === 'object' ? { ...r } : r))
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
          collapseMiLinkChildRowsToOnePerParticipant(merged, miChildFkConfigOfBinding(binding as any)),
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
          tableDisplayName: b.tableName,
          // 规范 key 的命名空间由「绑的是 DW 还是 RT 表」决定，缺了就会去 dw: 里找 rt: 的数据
          relationTableId: (b as { relationTableId?: number | null }).relationTableId,
          relationTableName: (b as { relationTableName?: string | null }).relationTableName
        },
        pk
      )
      if (isSharedAttachmentFileBinding(b)) {
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
        /**
         * fromOwnSlice (getSavedSubTableRowsFromVariables) already prefers self-owned rows among
         * its OWN candidate keys, but b.data may independently already hold the correct value from
         * an earlier hydration pass (e.g. hydrateChildSubTablesFromParentsNestedRows reading the
         * clicked row's own nested __subTables__). Do not let fromOwnSlice unconditionally win here
         * too — only its self-owned rows should override b.data; non-self-owned rows only fill gaps.
         */
        const existingData = Array.isArray(b.data) ? b.data : []
        // 本函数里当前 binding 是循环变量 b（`binding` 是别的函数的形参，这里不在作用域内）。
        // 每行都重算一次 FK 配置没有意义，循环外解析一次即可。
        const bFkConfig = miChildFkConfigOfBinding(b as never)
        const ownFromSlice = fromOwnSlice.filter(
          r => r && typeof r === 'object' && rowIsSelfOwnedByStructuralFk(r as Record<string, unknown>, pk, bFkConfig),
        )
        const restFromSlice = fromOwnSlice.filter(
          r => !(r && typeof r === 'object' && rowIsSelfOwnedByStructuralFk(r as Record<string, unknown>, pk, bFkConfig)),
        )
        let mergedRows = mergeSubTableRowsByRowId(restFromSlice, existingData, pk)
        if (ownFromSlice.length > 0) {
          mergedRows = mergeSubTableRowsByRowId(mergedRows, ownFromSlice, pk)
        }
        b.data = dropSubsumedSubTableRows(
          filterRowsForMiCollectionSubTableBinding(
            mergedRows,
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
      // Only MI dashboard bindings pull the global all-slices merge (it carries cross-slice MI status rows).
      // A plain structuralFk shared sub-table (e.g. ATM_Comment / ATM_Attachment keyed by case_row_id) must
      // use ONLY its own slice: allSlicesMerged pools every case sub-table row (including the sys_users
      // RELATED slice and the MI transaction rows), which surfaced as "-" ghost rows in My Request.
      const isMiDash = isMiDashboardSubTableBinding(
        b as { columns?: Array<{ field?: string }> | null; tableName?: string },
      )
      const fromVariables =
        useAllSlices && isMiDash
          ? mergeSubTableRowsByRowId(allSlicesMerged, bindingSaved ?? [], pk)
          : (bindingSaved ?? [])
      if (fromVariables.length === 0 && !(Array.isArray(b.data) && b.data.length > 0)) continue
      // Variables (backend MI overlay) win over binding rows polluted by enrich.
      let mergedRows = mergeSubTableRowsByRowId(Array.isArray(b.data) ? b.data : [], fromVariables, pk)
      if (!isMiDash) {
        // Drop rows that leaked in from other tables' slices (transaction/attachment rows in a comment grid).
        mergedRows = filterRowsForSharedProcessSubTableBinding(
          mergedRows,
          b as {
            columns?: Array<{ field?: string }> | null
            foreignKeyField?: string | null
            tableName?: string
            physicalTableName?: string
            tableId?: number | null
          },
        )
      }
      b.data = dropSubsumedSubTableRows(mergedRows)
    }
  }

  function backfillEmptySubTableBindingsFromVariables() {
    const savedMap = coerceSubTablesVariableToMap(formData.value.__subTables__)
    if (!savedMap) return
    formData.value = { ...formData.value, __subTables__: savedMap }

    const all = [
      ...subTableBindings.value,
      ...previousForms.value.flatMap(f => f.subTableBindings),
      ...Array.from(nodeFormMap.value.values()).flatMap(n => n.subTableBindings)
    ]

    for (const b of all) {
      if (isSharedAttachmentFileBinding(b)) {
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

      const found = resolveSameTableIdSliceForBinding(b, savedMap, lastBindingRelationTableMap.value)
      if (found) {
        b.data = found.map((r: any) => (r && typeof r === 'object' ? { ...r } : r))
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
