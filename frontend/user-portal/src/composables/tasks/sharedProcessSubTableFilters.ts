/**
 * Row filters for process-level shared sub-tables (attachment.main_id etc.):
 * drop MI participant / foreign binding rows that leaked into shared bindings and finalize rows.
 */

import { pickNonEmptyAttachmentFile } from './internal'
import {
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  isSharedAttachmentFileBinding,
  isSubTableMiDashboardRow,
  isSubTableRowMetaField,
  stripSubTableRowMetaFields,
} from './subTableBindingKinds'
import { dropSubsumedSubTableRows } from './subTableRowNormalize'

function sharedBindingRowHasNonIdColumnData(
  rec: Record<string, unknown>,
  colFields: Set<string>,
): boolean {
  const fields =
    colFields.size > 0
      ? [...colFields].filter(f => f !== 'id')
      : Object.keys(rec).filter(k => k !== 'id' && !isSubTableRowMetaField(k))
  if (fields.length === 0) return false
  return fields.some(f => {
    const v = rec[f]
    return v != null && v !== '' && !(typeof v === 'string' && v.trim() === '')
  })
}

/** Row ids from MI / subtable slices — must not appear as attachment.id (separate tables, unrelated PKs). */
export type SharedProcessSubTableFilterContext = {
  foreignSubTableRowIds?: Set<string>
}

function isLeakedForeignRowOnSharedAttachment(
  rec: Record<string, unknown>,
  colFields: Set<string>,
  foreignSubTableRowIds?: Set<string>,
): boolean {
  if (isSubTableMiDashboardRow(rec)) return true

  // Genuine attachment rows are never foreign leaks — even when the same UUID was
  // incorrectly duplicated into a subtable slice and registered as "foreign".
  if (pickNonEmptyAttachmentFile(rec)) {
    if (rec.name != null && String(rec.name).trim() !== '' && !colFields.has('name')) {
      return true
    }
    return false
  }

  const rowId = rec.id != null ? String(rec.id).trim() : ''
  if (rowId && foreignSubTableRowIds) {
    const foreign =
      foreignSubTableRowIds instanceof Set
        ? foreignSubTableRowIds
        : new Set(
            [...(foreignSubTableRowIds as Iterable<unknown>)].map(v => String(v).trim()).filter(Boolean),
          )
    if (foreign.has(rowId)) return true
  }

  // Backend MI overlay may stamp id_idw on persisted attachment rows — not a subtable leak.
  if (rec.id_idw != null && String(rec.id_idw).trim() !== '' && !colFields.has('id_idw')) {
    return true
  }

  if (rec.name != null && String(rec.name).trim() !== '' && !colFields.has('name')) {
    return true
  }

  return false
}

/** Drop attachment-shaped rows (id + file only) that leaked into an MI / subtable binding grid. */
export function filterRowsForMiParticipantSubTableBinding(
  rows: any[] | undefined | null,
  binding: {
    columns?: Array<{ field?: string }> | null
    tableName?: string
  },
): any[] {
  if (!Array.isArray(rows) || rows.length === 0) return []
  const colFields = new Set(
    (binding.columns ?? [])
      .map(c => (c?.field != null ? String(c.field).trim() : ''))
      .filter(Boolean),
  )
  return rows
    .filter(row => {
      if (!row || typeof row !== 'object') return false
      const rec = row as Record<string, unknown>
      if (colFields.has('file') && pickNonEmptyAttachmentFile(row)) {
        if (isSubTableMiDashboardRow(rec)) return true
        if (rec.sub_task_id != null && String(rec.sub_task_id).trim() !== '') return true
        if (rec.id_idw != null && String(rec.id_idw).trim() !== '') return true
        if (rec.name != null && String(rec.name).trim() !== '') return true
        return false
      }
      if (!pickNonEmptyAttachmentFile(row)) return true
      if (isSubTableMiDashboardRow(rec)) return true
      const name = rec.name
      if (name != null && String(name).trim() !== '') return true
      if (rec.id_idw != null && String(rec.id_idw).trim() !== '') return true
      return false
    })
}

/**
 * Drop MI participant / foreign binding rows that leaked into a process-level shared sub-table (e.g. attachment.main_id).
 */
export function filterRowsForSharedProcessSubTableBinding(
  rows: any[] | undefined | null,
  binding: {
    columns?: Array<{ field?: string }> | null
    foreignKeyField?: string | null
    tableName?: string
    physicalTableName?: string
    tableId?: number | null
  },
  filterContext?: SharedProcessSubTableFilterContext,
): any[] {
  if (!Array.isArray(rows) || rows.length === 0) return []
  if (isMiParticipantScopedSubTableBinding(binding)) {
    return filterRowsForMiParticipantSubTableBinding(rows, binding)
  }

  const colFields = new Set(
    (binding.columns ?? [])
      .map(c => (c?.field != null ? String(c.field).trim() : ''))
      .filter(Boolean),
  )
  const attachmentBinding = isSharedAttachmentFileBinding(binding)
  const foreignIds = filterContext?.foreignSubTableRowIds

  return rows.filter(row => {
    if (!row || typeof row !== 'object') return false
    const rec = row as Record<string, unknown>

    if (attachmentBinding && isLeakedForeignRowOnSharedAttachment(rec, colFields, foreignIds)) {
      return false
    }

    if (!attachmentBinding && isSubTableMiDashboardRow(rec)) return false

    if (!attachmentBinding && rec.id_idw != null && String(rec.id_idw).trim() !== '' && !colFields.has('id_idw')) {
      return false
    }

    if (!attachmentBinding && rec.name != null && String(rec.name).trim() !== '' && !colFields.has('name')) {
      return false
    }

    const hasOwnData = sharedBindingRowHasNonIdColumnData(rec, colFields)
    if (hasOwnData) return true

    if (isSubTableMiDashboardRow(rec)) return false

    if (rec.id_idw != null && String(rec.id_idw).trim() !== '' && !colFields.has('id_idw')) {
      return false
    }

    if (rec.name != null && String(rec.name).trim() !== '' && !colFields.has('name')) {
      return false
    }

    return true
  })
}

/** Strip meta, drop foreign MI rows, and collapse id-only ghosts for shared process sub-tables. */
export function finalizeSharedProcessSubTableBindingRows(
  rows: any[] | undefined | null,
  binding: {
    columns?: Array<{ field?: string }> | null
    foreignKeyField?: string | null
    tableName?: string
    physicalTableName?: string
    tableId?: number | null
  },
  filterContext?: SharedProcessSubTableFilterContext,
): any[] {
  const preserveMiFields = isMiDashboardSubTableBinding(binding)
  const cleaned = filterRowsForSharedProcessSubTableBinding(rows, binding, filterContext).map(row => {
    if (!row || typeof row !== 'object') return row
    return preserveMiFields
      ? row
      : stripSubTableRowMetaFields(row as Record<string, unknown>)
  })
  return dropSubsumedSubTableRows(cleaned)
}
