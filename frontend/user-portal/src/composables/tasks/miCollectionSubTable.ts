/**
 * MI collection (Sub Task / participant dashboard) sub-table helpers: leak filtering,
 * designer-PK merge choke point, stale sibling slice sync decisions and collection slice key sets.
 */

import { pickNonEmptyAttachmentFile } from './internal'
import { normalizeSubTableName } from './subTableCore'
import {
  isFileOnlySubTableBinding,
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  isSubTableMiDashboardRow,
} from './subTableBindingKinds'
import { dropSubsumedSubTableRows } from './subTableRowNormalize'
import { mergeSubTableRowsByRowId, rowResolvesDesignerPrimaryKey } from './subTableRowMerge'
import { collectSubTableSliceArraysDeep } from './subTableSliceResolve'

export type MiCollectionBindingPk = {
  primaryKeyFields?: string[] | null
  columns?: Array<{ field?: string }> | null
}

/**
 * Designer PK columns for an MI collection binding.
 * FALLBACK(migration): legacy collections without API PK metadata used {@code id_idw}.
 */
export function resolveMiCollectionPrimaryKeyFields(binding: MiCollectionBindingPk): string[] {
  const fromBinding = (binding.primaryKeyFields ?? [])
    .map(f => String(f).trim())
    .filter(Boolean)
  if (fromBinding.length > 0) return fromBinding
  return ['id_idw']
}

/** Drop attachment/file-only leak rows and rows missing a complete designer PK. */
export function filterRowsForMiCollectionSubTableBinding(
  rows: any[] | undefined | null,
  binding: MiCollectionBindingPk,
): any[] {
  if (!Array.isArray(rows) || rows.length === 0) return []
  const pkCols = resolveMiCollectionPrimaryKeyFields(binding)
  return rows.filter(row => {
    if (!row || typeof row !== 'object') return false
    const rec = row as Record<string, unknown>
    if (!rowResolvesDesignerPrimaryKey(rec, pkCols)) return false
    if (pickNonEmptyAttachmentFile(rec)) return false
    return true
  })
}

/**
 * Unified MI collection dedupe: merge sources by designer PK (later sources win on conflicts;
 * filled fields beat empty thin snapshots), drop rows without a complete PK, then drop ghost subsets.
 *
 * Call sites that previously did {@code mergeSubTableRowsByRowId} + {@link finalizeMiCollectionSubTableBindingRows}
 * should prefer this single choke point (current task / snapshot / resync slices as separate sources).
 */
export function mergeMiCollectionSubTableRows(
  sources: Array<any[] | undefined | null>,
  binding: MiCollectionBindingPk,
): any[] {
  const pkCols = resolveMiCollectionPrimaryKeyFields(binding)
  let merged: any[] = []
  for (const src of sources) {
    if (!Array.isArray(src) || src.length === 0) continue
    const cleaned = filterRowsForMiCollectionSubTableBinding(src, binding)
    if (cleaned.length === 0) continue
    merged = mergeSubTableRowsByRowId(merged, cleaned, pkCols)
  }
  return dropSubsumedSubTableRows(merged)
}

/** Collapse one MI collection slice by designer PK (same semantics as multi-source merge). */
export function finalizeMiCollectionSubTableBindingRows(
  rows: any[] | undefined | null,
  binding: MiCollectionBindingPk,
): any[] {
  return mergeMiCollectionSubTableRows([rows], binding)
}

/**
 * Assignment task only: merge active binding rows into another numeric slice when both describe
 * the same MI collection table — never into attachment / link-child bindings.
 */
export function shouldSyncStaleSiblingSubTableSlice(
  target: unknown[],
  sourceBinding: {
    bindingId?: number
    tableId?: number | null
    tableName?: string
    columns?: Array<{ field?: string }> | null
    primaryKeyFields?: string[] | null
  },
  allBindings: Array<{
    bindingId: number
    tableId?: number | null
    tableName?: string
    columns?: Array<{ field?: string }> | null
  }>,
  sliceKey: string,
): boolean {
  if (!isMiDashboardSubTableBinding(sourceBinding)) return false
  const pkCol = resolveMiCollectionPrimaryKeyFields(sourceBinding)[0] ?? 'id_idw'
  const bid = Number(sliceKey)
  const peer = allBindings.find(b => Number(b.bindingId) === bid)
  if (peer) {
    if (isFileOnlySubTableBinding(peer)) return false
    if (
      isMiParticipantScopedSubTableBinding(peer as { columns?: Array<{ field?: string }> | null; foreignKeyField?: string | null; tableName?: string })
      && !isMiDashboardSubTableBinding(peer)
    ) {
      return false
    }
    const srcTid = sourceBinding.tableId != null ? Number(sourceBinding.tableId) : NaN
    const peerTid = peer.tableId != null ? Number(peer.tableId) : NaN
    if (Number.isFinite(srcTid) && Number.isFinite(peerTid) && srcTid !== peerTid) return false
    return isMiDashboardSubTableBinding(peer)
  }
  const hasPk = target.some(
    r => r && typeof r === 'object' && rowResolvesDesignerPrimaryKey(r, [pkCol]),
  )
  if (!hasPk) return false
  return !target.every(
    r => r && typeof r === 'object' && pickNonEmptyAttachmentFile(r) && !(r as Record<string, unknown>)[pkCol],
  )
}

export function subTableVariablesIncludeMiRows(
  savedSubTables: Record<string, unknown> | null | undefined,
): boolean {
  if (!savedSubTables || typeof savedSubTables !== 'object') return false
  for (const arr of collectSubTableSliceArraysDeep(savedSubTables)) {
    for (const row of arr) {
      if (row && typeof row === 'object' && isSubTableMiDashboardRow(row as Record<string, unknown>)) {
        return true
      }
    }
  }
  return false
}

/**
 * Slice keys in {@code __subTables__} that hold MI collection (Sub Task / dashboard) rows, so callers can tell
 * {@link scrubMiCorruptLinkChildRowsForParent} to leave their {@code id_idw} (the participant primary key) intact.
 * Covers binding-id keys (current + sibling/copied ids sharing the collection {@code tableId}) and table-name keys.
 */
export function buildMiCollectionSliceKeySet(
  bindings: Array<{
    bindingId: number
    tableName?: string
    physicalTableName?: string
    tableId?: number | null
    columns?: Array<{ field?: string }> | null
  }>,
  bindingTableById: Map<number, number | null>,
  scopeSubTableName?: string | null,
): Set<string> {
  const keys = new Set<string>()
  const addName = (s?: string | null) => {
    if (s == null) return
    const t = String(s).trim()
    if (!t) return
    keys.add(t)
    const n = normalizeSubTableName(t)
    if (n) keys.add(n)
  }
  const collTids = new Set<number>()
  const wantName = scopeSubTableName ? normalizeSubTableName(String(scopeSubTableName)) : null
  addName(scopeSubTableName)
  for (const b of bindings) {
    const matchesName =
      wantName != null &&
      [b.physicalTableName, b.tableName].some(c => c != null && normalizeSubTableName(String(c)) === wantName)
    if (!matchesName && !isMiDashboardSubTableBinding(b)) continue
    keys.add(String(b.bindingId))
    addName(b.tableName)
    addName(b.physicalTableName)
    const tid = b.tableId != null ? Number(b.tableId) : bindingTableById.get(b.bindingId)
    if (tid != null && Number.isFinite(Number(tid))) collTids.add(Number(tid))
  }
  if (collTids.size > 0) {
    for (const [bid, tid] of bindingTableById.entries()) {
      if (tid != null && collTids.has(Number(tid))) keys.add(String(bid))
    }
  }
  return keys
}
