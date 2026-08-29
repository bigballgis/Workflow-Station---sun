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
import { resolveMiChildStructuralParentFk } from './miLinkChildIdentity'
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

  /**
   * A structural FK to an MI participant ({@code sub_task_id} etc.) marks the row as a link-child of
   * some other table — an attachment row is keyed to the main record, never to a participant. This
   * catches leaks the {@code foreignSubTableRowIds} registry misses: that registry only walks slices
   * whose KEY looks participant-ish ({@code subtable}/{@code participants}, or table id 20/21), so a
   * Function Unit whose participant table has different ids (FU 50005 uses 50331, keyed numerically
   * as {@code 50544}) contributes no ids at all and every id-only ghost slipped through.
   */
  if (resolveMiChildStructuralParentFk(rec)) return true

  /**
   * An "attachment" row with no file AND no value in any of its own non-id columns carries nothing
   * this table can display — the grid renders it as a row of "-". Whatever produced it (a foreign
   * row projected down to its id, a stale placeholder), it is not an attachment.
   */
  if (!sharedBindingRowHasNonIdColumnData(rec, colFields)) return true

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

    // Rows carrying real data for this binding's own columns are ALWAYS kept. The id_idw /
    // name "foreign row" heuristics below only apply to rows WITHOUT own column data —
    // id_idw is the DW default sub-table PK, so a data-bearing row whose list view simply
    // doesn't display the PK must never be treated as an MI leak (it emptied whole tables).
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
