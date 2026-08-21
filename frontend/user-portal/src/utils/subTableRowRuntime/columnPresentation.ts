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
 * Applies Process Design task-node field permissions (composite `${bindingId}:${fieldName}`
 * keys, see field-level sub-table permission extension) as {@code readonly} overrides on the
 * Add/Edit dialog columns of one sub-table binding.
 *
 * No entry for this binding at all → every column unaffected (backward-compatible default for
 * Function Units that never configured sub-table field permissions). A column already
 * `readonly` from FK/auto-PK presentation stays readonly regardless (this only narrows, never
 * widens, editability).
 */
export function applyFieldPermissionsToDialogColumns(
  columns: DialogColumn[],
  bindingId: number | null | undefined,
  fieldPermissions: Record<string, string> | null | undefined,
): DialogColumn[] {
  if (bindingId == null || !fieldPermissions) return columns
  const prefix = `${bindingId}:`
  const hasAnyForBinding = Object.keys(fieldPermissions).some(key => key.startsWith(prefix))
  if (!hasAnyForBinding) return columns
  return columns.map(col => {
    if (col.readonly) return col
    const permission = fieldPermissions[`${prefix}${col.field}`]
    if (permission != null && String(permission).toUpperCase() === 'READONLY') {
      return { ...col, readonly: true }
    }
    return col
  })
}

/** Inline / modal subForm fields: auto-PK and string FK values must not use el-input-number. */
export function applyFieldDefinitionsToFormFields<
  T extends { key: string; type?: string; readonly?: boolean },
>(fields: T[], fieldDefinitions?: BindingFieldDefinition[] | null): T[] {
  const fieldByName = new Map((fieldDefinitions ?? []).map(f => [f.fieldName, f]))
  return fields.map(field => {
    const def = fieldByName.get(field.key)
    if (!def) return field
    let next = { ...field }
    if (next.type === 'number' && pkNeedsAllocation(def) && pkAllocationYieldsString(def)) {
      next = { ...next, type: 'text', readonly: true }
    }
    return next
  })
}
