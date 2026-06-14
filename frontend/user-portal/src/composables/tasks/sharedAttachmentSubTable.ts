/**
 * Shared attachment sub-table (attachment.main_id) materialization: merge {@code __subTables__} slices,
 * collect foreign MI / subtable row ids and drop leaked rows. Used by To Do and My Request for parity.
 */

import { pickNonEmptyAttachmentFile } from './internal'
import { normalizeSubTableName } from './subTableCore'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import {
  isMiParticipantScopedSubTableBinding,
  isSharedAttachmentFileBinding,
  isSubTableMiDashboardRow,
} from './subTableBindingKinds'
import { mergeAllSlicesForSharedProcessSubTableBinding } from './subTableSliceMerge'
import {
  finalizeSharedProcessSubTableBindingRows,
  type SharedProcessSubTableFilterContext,
} from './sharedProcessSubTableFilters'

export type SharedAttachmentBindingLike = {
  bindingId?: number
  tableId?: number | null
  tableName?: string
  physicalTableName?: string
  foreignKeyField?: string | null
  columns?: Array<{ field?: string }> | null
  primaryKeyFields?: string[] | null
  data: any[]
}

/**
 * Shared attachment bindings: merge {@code __subTables__} slices, drop MI leaks.
 * Primary-table scalars are never projected into sub-table rows.
 * Used by To Do and My Request for parity.
 */
export function applySharedAttachmentFinalizeAndMaterialize<
  T extends SharedAttachmentBindingLike,
>(
  bindings: T[],
  _topLevelValues?: Record<string, unknown> | null | undefined,
  options?: {
    flattened?: Record<string, unknown> | null
    bindingTableById?: Map<number, number | null>
  },
): void {
  const rtMap = options?.bindingTableById ?? new Map<number, number | null>()
  const flat = options?.flattened ?? null
  const foreignSubTableRowIds =
    flat != null ? collectForeignSubTableRowIdsFromVariables(flat, rtMap) : undefined
  const filterContext: SharedProcessSubTableFilterContext | undefined =
    foreignSubTableRowIds && foreignSubTableRowIds.size > 0
      ? { foreignSubTableRowIds }
      : undefined
  for (const binding of bindings) {
    if (isMiParticipantScopedSubTableBinding(binding)) continue
    if (!isSharedAttachmentFileBinding(binding)) continue
    if (flat) {
      const merged = mergeAllSlicesForSharedProcessSubTableBinding(
        flat,
        binding as {
          bindingId: number
          tableId?: number | null
          tableName?: string
          physicalTableName?: string
          primaryKeyFields?: string[] | null
        },
        rtMap,
      )
      let canonical = merged
      if (canonical.length === 0) {
        const bid = binding.bindingId
        const keysToTry = new Set<string | number>()
        for (const key of [bid, String(bid), binding.tableName, binding.physicalTableName, 'attachment']) {
          if (key == null || String(key).trim() === '') continue
          keysToTry.add(key)
          keysToTry.add(normalizeSubTableName(String(key)))
        }
        const tableIdRaw =
          binding.tableId != null ? Number(binding.tableId) : rtMap.get(Number(binding.bindingId))
        if (tableIdRaw != null && Number.isFinite(tableIdRaw)) {
          for (const [sibBid, tid] of rtMap.entries()) {
            if (Number(tid) !== Number(tableIdRaw)) continue
            keysToTry.add(sibBid)
            keysToTry.add(String(sibBid))
          }
        }
        for (const key of keysToTry) {
          const slice = flat[key as string] ?? flat[normalizeSubTableName(String(key))]
          if (Array.isArray(slice) && slice.length > 0) {
            canonical = mergeSubTableRowsByRowId(
              canonical,
              slice as any[],
              binding.primaryKeyFields ?? null,
            )
          }
        }
      }
      if (canonical.length > 0) {
        binding.data = mergeSubTableRowsByRowId([], canonical, binding.primaryKeyFields ?? null)
      }
    }
    binding.data = finalizeSharedProcessSubTableBindingRows(binding.data, binding, filterContext)
  }
}

function isAttachmentBindingSliceKey(
  key: string,
  bindingTableById: Map<number, number | null>,
): boolean {
  const n = normalizeSubTableName(key)
  if (n === 'attachment') return true
  const kid = Number(key)
  if (Number.isFinite(kid) && (kid === 103 || kid === 104)) return true
  if (!Number.isFinite(kid)) return false
  const tid = bindingTableById.get(kid)
  return tid === 74
}

/** MI participant / subtable slices — row ids here must not appear on attachment.id. */
function isMiSubTableParticipantSliceKey(
  key: string,
  bindingTableById: Map<number, number | null>,
): boolean {
  const n = normalizeSubTableName(key)
  if (n === 'subtable' || n === 'subtable2' || n === 'participants') return true
  const kid = Number(key)
  if (!Number.isFinite(kid)) return false
  const tid = bindingTableById.get(kid)
  return tid === 20 || tid === 21
}

function addRowIdToForeignSet(row: unknown, ids: Set<string>): void {
  if (!row || typeof row !== 'object') return
  const id = (row as Record<string, unknown>).id
  if (id != null && String(id).trim() !== '') ids.add(String(id).trim())
}

function collectForeignSubTableRowIdsFromRowWalk(
  rows: unknown[],
  ids: Set<string>,
  bindingTableById: Map<number, number | null>,
): void {
  for (const row of rows) {
    if (!row || typeof row !== 'object') continue
    const rec = row as Record<string, unknown>
    if (!isPureSharedAttachmentFileRow(rec)) {
      addRowIdToForeignSet(row, ids)
    }
    const nest = rec.__subTables__
    if (!nest || typeof nest !== 'object') continue
    for (const [childKey, childVal] of Object.entries(nest as Record<string, unknown>)) {
      if (!Array.isArray(childVal)) continue
      if (isAttachmentBindingSliceKey(childKey, bindingTableById)) continue
      collectForeignSubTableRowIdsFromRowWalk(childVal, ids, bindingTableById)
    }
  }
}

/**
 * Collect row ids from MI / subtable participant slices only (not every non-attachment key).
 * Used to drop attachment rows that reused a subtable participant id (e.g. 666 + name).
 */
export function collectForeignSubTableRowIdsFromVariables(
  savedSubTables: Record<string, unknown> | null | undefined,
  bindingTableById: Map<number, number | null>,
): Set<string> {
  const ids = new Set<string>()
  if (!savedSubTables || typeof savedSubTables !== 'object') return ids

  for (const [key, val] of Object.entries(savedSubTables)) {
    if (!Array.isArray(val)) continue
    if (isAttachmentBindingSliceKey(key, bindingTableById)) continue
    if (isMiSubTableParticipantSliceKey(key, bindingTableById)) {
      collectForeignSubTableRowIdsFromRowWalk(val, ids, bindingTableById)
      continue
    }
    for (const row of val) {
      if (!row || typeof row !== 'object') continue
      const rec = row as Record<string, unknown>
      if (isSubTableMiDashboardRow(rec)) addRowIdToForeignSet(row, ids)
      const nest = rec.__subTables__
      if (!nest || typeof nest !== 'object') continue
      for (const [childKey, childVal] of Object.entries(nest as Record<string, unknown>)) {
        if (!Array.isArray(childVal)) continue
        if (isMiSubTableParticipantSliceKey(childKey, bindingTableById)) {
          collectForeignSubTableRowIdsFromRowWalk(childVal, ids, bindingTableById)
        }
      }
    }
  }
  return ids
}

function isPureSharedAttachmentFileRow(rec: Record<string, unknown>): boolean {
  if (!pickNonEmptyAttachmentFile(rec)) return false
  if (isSubTableMiDashboardRow(rec)) return false
  if (rec.sub_task_id != null && String(rec.sub_task_id).trim() !== '') return false
  if (rec.id_idw != null && String(rec.id_idw).trim() !== '') return false
  if (rec.name != null && String(rec.name).trim() !== '') return false
  return true
}
