/**
 * Sub-table row runtime — FK/auto-PK column & form-field presentation (PRD S5).
 * Parity with developer-workstation Form Preview runtime.
 */
import type { DialogColumn } from '../../components/subTableAddDialogHelpers'
import {
  type FieldFkMeta,
  isFkHidden,
  isFkReadonly,
} from '../tableFkRuntime'
import { applyComputedReadonlyToFormFields, isComputedColumn } from '../computedFieldRuntime'
import { pkAllocationYieldsString, pkNeedsAllocation } from './pkPredicates'
import type { BindingFieldDefinition } from './types'

function applyAutoPkColumnPresentation(
  col: DialogColumn,
  fieldDef: BindingFieldDefinition,
): DialogColumn {
  if (!pkNeedsAllocation(fieldDef)) return col
  let next: DialogColumn = { ...col, readonly: true }
  if (next.type === 'number' && pkAllocationYieldsString(fieldDef)) {
    next = { ...next, type: 'text' }
  }
  return next
}

export function applyFkPresentationToDialogColumns(
  columns: DialogColumn[],
  fkMetas: FieldFkMeta[],
  fieldDefinitions?: BindingFieldDefinition[] | null,
): { visibleColumns: DialogColumn[]; allColumns: DialogColumn[] } {
  const metaByField = new Map(fkMetas.map(m => [m.fieldName, m]))
  const fieldByName = new Map((fieldDefinitions ?? []).map(f => [f.fieldName, f]))
  const allColumns = columns.map(col => {
    let next = col
    const meta = metaByField.get(col.field)
    if (meta?.isForeignKey && isFkReadonly(meta)) {
      next = { ...next, readonly: true }
    }
    const fieldDef = fieldByName.get(col.field)
    if (fieldDef) {
      next = applyAutoPkColumnPresentation(next, fieldDef)
      if (isComputedColumn(fieldDef)) {
        next = { ...next, readonly: true }
      }
    }
    return next
  })
  const visibleColumns = allColumns.filter(col => {
    const meta = metaByField.get(col.field)
    return !meta || !isFkHidden(meta)
  })
  return { visibleColumns, allColumns }
}

/**
 * Inline / modal subForm fields: auto-PK and string FK values must not use el-input-number, and a
 * computed column must not be editable at all — the server overwrites whatever is typed there on
 * every write, so an editable input would only promise an edit it cannot keep.
 */
export function applyFieldDefinitionsToFormFields<
  T extends { key: string; type?: string; readonly?: boolean },
>(fields: T[], fieldDefinitions?: BindingFieldDefinition[] | null): T[] {
  const fieldByName = new Map((fieldDefinitions ?? []).map(f => [f.fieldName, f]))
  const withPkPresentation = fields.map(field => {
    const def = fieldByName.get(field.key)
    if (!def) return field
    if (field.type === 'number' && pkNeedsAllocation(def) && pkAllocationYieldsString(def)) {
      return { ...field, type: 'text', readonly: true }
    }
    return field
  })
  // Single owner of the computed ⇒ read-only decision, and the only pass that descends into
  // layout containers (a computed input nested in a card must not stay editable).
  return applyComputedReadonlyToFormFields(withPkPresentation, fieldDefinitions)
}
