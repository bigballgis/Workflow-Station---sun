import type { Ref } from 'vue'
import {
  normalizeSubTableName,
  flattenNestedSubTableRowsIntoPayload,
  normalizeSubTableRowsForBinding,
} from '@/composables/tasks/shared'
import {
  deriveColumnsFromRelationFieldDefinitions,
  resolveSubTableSchemaByTableId,
  enrichColumnsWithTableFieldDisplayNames,
  defaultAttachmentListColumns,
  SHARED_ATTACHMENT_RELATION_TABLE_ID,
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

  function isPortalSharedAttachmentTableBinding(b: {
    bindingId?: number
    tableId?: number | null
    tableName?: string
    foreignKeyField?: string | null
  }): boolean {
    const tableIdNum = b.tableId != null ? Number(b.tableId) : NaN
    const tn = normalizeSubTableName(String(b.tableName ?? ''))
    if (Number.isFinite(tableIdNum) && tableIdNum === SHARED_ATTACHMENT_RELATION_TABLE_ID) return true
    if (tn === 'attachment') return true
    return String(b.foreignKeyField ?? '').trim().toLowerCase() === 'main_id' && tn === 'attachment'
  }

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
    if ((!columns || columns.length === 0) && isPortalSharedAttachmentTableBinding(b)) {
      columns = defaultAttachmentListColumns()
    }
    if (Number.isFinite(tableIdNum) && columns?.length) {
      columns = enrichColumnsWithTableFieldDisplayNames(columns, tableIdNum, caches.cachedRelationTableFieldIndex)
    }
    return Array.isArray(columns) ? columns : []
  }

  /** Match task detail / autosave: key __subTables__ by binding id and table display name so downstream forms with new bindingIds can resolve rows. */
  function buildStartFormSubTablesPayload(): Record<string, unknown> {
    const subTables: Record<string, unknown> = {}
    for (const b of subTableBindings.value) {
      const rows = normalizeSubTableRowsForBinding(Array.isArray(b.data) ? b.data : [])
      subTables[b.bindingId] = rows
      subTables[String(b.bindingId)] = rows
      const tn = String(b.tableName || '').trim()
      if (tn) {
        subTables[tn] = rows
        subTables[normalizeSubTableName(tn)] = rows
      }
    }
    flattenNestedSubTableRowsIntoPayload(subTables)
    return subTables
  }

  return {
    isPortalSharedAttachmentTableBinding,
    resolveSubTableBindingColumnsForStart,
    buildStartFormSubTablesPayload,
  }
}
