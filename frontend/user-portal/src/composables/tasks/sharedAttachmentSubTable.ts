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
    binding.data = finalizeSharedProcessSubTableBindingRows(binding.data, binding)
  }
}

function isPureSharedAttachmentFileRow(rec: Record<string, unknown>): boolean {
  if (!pickNonEmptyAttachmentFile(rec)) return false
  if (isSubTableMiDashboardRow(rec)) return false
  if (rec.sub_task_id != null && String(rec.sub_task_id).trim() !== '') return false
  if (rec.id_idw != null && String(rec.id_idw).trim() !== '') return false
  if (rec.name != null && String(rec.name).trim() !== '') return false
  return true
}
