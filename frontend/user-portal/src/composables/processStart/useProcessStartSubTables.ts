import type { Ref } from 'vue'
import { readSubTableRows, writeSubTableRows } from '@/composables/tasks/subTableStore'
import {
  flattenNestedSubTableRowsIntoPayload,
  flattenSliceMapsFromBindings,
  normalizeSubTableRowsForBinding,
} from '@/composables/tasks/shared'
import {
  deriveColumnsFromRelationFieldDefinitions,
  mergeMissingTableFieldColumns,
  resolveSubTableSchemaByTableId,
  enrichColumnsWithTableFieldDisplayNames,
  type RelationFieldDef,
  type DialogColumn,
} from '@/components/subTableAddDialogHelpers'
import type { ProcessStartSubTableBinding } from './useProcessStartState'

/**
 * Sub-table column resolution + draft/submit payload assembly for the start form.
 * Logic identical to the original start.vue inline functions; mutable caches passed by reference.
 */
export function createProcessStartSubTables(deps: {
  caches: {
    cachedContentForms: unknown[]
    cachedRelationTableFieldIndex: Map<number, RelationFieldDef[]>
  }
  subTableBindings: Ref<ProcessStartSubTableBinding[]>
  /** From the form-parsing composable — sub-table display column derivation. */
  deriveColumnsFromBinding: (
    binding: any,
    subForms?: Record<string, any>,
    formConfig?: Record<string, any>,
  ) => DialogColumn[]
}) {
  const { caches, subTableBindings, deriveColumnsFromBinding } = deps

  function resolveSubTableBindingColumnsForStart(
    b: {
      bindingId?: number
      tableId?: number | null
      tableName?: string
      foreignKeyField?: string | null
      subFormConfig?: { rule?: unknown[] }
    },
    subForms: Record<string, any>,
    formConfig: Record<string, any>,
  ): ReturnType<typeof deriveColumnsFromBinding> {
    let columns = deriveColumnsFromBinding(b, subForms, formConfig)
    const tableIdNum = b.tableId != null ? Number(b.tableId) : NaN
    if ((!Array.isArray(columns) || columns.length === 0) && Number.isFinite(tableIdNum) && caches.cachedContentForms.length > 0) {
      const alt = resolveSubTableSchemaByTableId(tableIdNum, caches.cachedContentForms, b.bindingId)
      if (alt) {
        columns = deriveColumnsFromBinding({ ...b, bindingId: alt.bindingId }, alt.subForms, alt.formConfig)
      }
      if ((!columns || columns.length === 0) && caches.cachedRelationTableFieldIndex.has(tableIdNum)) {
        columns = deriveColumnsFromRelationFieldDefinitions(caches.cachedRelationTableFieldIndex.get(tableIdNum)!)
      }
    }
    // DW parity: designed columns are returned untouched; table schema is only a
    // fallback when no columns were designed for this binding.
    if (Number.isFinite(tableIdNum)) {
      columns = mergeMissingTableFieldColumns(
        Array.isArray(columns) ? columns : [],
        caches.cachedRelationTableFieldIndex.get(tableIdNum),
      )
    }
    if (Number.isFinite(tableIdNum) && columns?.length) {
      columns = enrichColumnsWithTableFieldDisplayNames(columns, tableIdNum, caches.cachedRelationTableFieldIndex)
    }
    return Array.isArray(columns) ? columns : []
  }

  /** Persist one slice per designer table (`dw:` / `rt:`), then flatten nested Link Form deletes. */
  function buildStartFormSubTablesPayload(): Record<string, unknown> {
    const subTables: Record<string, unknown> = {}
    const { primaryKeyFieldsBySliceKey, parentLink } = flattenSliceMapsFromBindings(
      subTableBindings.value,
    )
    for (const b of subTableBindings.value) {
      const rows = normalizeSubTableRowsForBinding(Array.isArray(b.data) ? b.data : [])
      writeSubTableRows(subTables, b, rows)
    }
    flattenNestedSubTableRowsIntoPayload(subTables, 8, primaryKeyFieldsBySliceKey, parentLink)
    return subTables
  }

  /**
   * Restore start-form bindings from a draft `__subTables__` bag.
   * Canonical store keys are the write path; binding-id keys remain for older drafts.
   */
  function hydrateStartFormBindingsFromDraftStore(st: Record<string, unknown>): void {
    const { primaryKeyFieldsBySliceKey, parentLink } = flattenSliceMapsFromBindings(
      subTableBindings.value,
    )
    flattenNestedSubTableRowsIntoPayload(st, 8, primaryKeyFieldsBySliceKey, parentLink)
    for (const binding of subTableBindings.value) {
      const canonical = readSubTableRows(st, binding)
      const legacy = st[binding.bindingId] ?? st[String(binding.bindingId)]
      const saved = canonical ?? (Array.isArray(legacy) ? legacy : undefined)
      if (Array.isArray(saved)) {
        binding.data = normalizeSubTableRowsForBinding(saved)
      }
    }
  }

  return {
    resolveSubTableBindingColumnsForStart,
    buildStartFormSubTablesPayload,
    hydrateStartFormBindingsFromDraftStore,
  }
}
