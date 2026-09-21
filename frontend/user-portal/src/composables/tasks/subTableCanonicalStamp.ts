import { subTableStoreKey, type SubTableStoreBindingLike } from './subTableStore'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import { PLATFORM_ROW_UUID_FIELD } from '@/utils/subTableRowIdentity'
import { readSubTableRows } from './subTableStore'
import { projectSavedRowsForBinding, type FilterProjectionBinding, type FilterProjectionFormContext } from './subTableFilterProjection'

/** Transport metadata: which binding claimed which rows of a shared table-keyed store. */
export interface SubTableBindingScope {
  bindingId: string
  storeKey: string
  rowKeys: Array<Record<string, unknown>>
  emptied: boolean
  deletedRows?: Array<Record<string, unknown>>
}

export interface CanonicalStampBinding extends SubTableStoreBindingLike {
  bindingId?: number | string
  primaryKeyFields?: string[] | null
}

/** Store keys used by more than one binding on this form. */
export function storeKeysSharedByMultipleBindings(
  bindings: CanonicalStampBinding[] | null | undefined,
): Set<string> {
  const counts = new Map<string, number>()
  for (const binding of bindings ?? []) {
    const key = subTableStoreKey(binding)
    if (!key) continue
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  const shared = new Set<string>()
  for (const [key, n] of counts) {
    if (n > 1) shared.add(key)
  }
  return shared
}

/**
 * Write rows onto the canonical table key. Shared keys union-merge by PK so a later
 * binding cannot drop another binding's rows by replacing the whole array.
 */
export function stampCanonicalStoreRows(
  subTables: Record<string, unknown>,
  subTableData: Record<string, Array<Record<string, unknown>>>,
  binding: CanonicalStampBinding,
  rows: unknown[],
  sharedKeys: Set<string>,
): string | null {
  const key = subTableStoreKey(binding)
  if (!key) return null
  const incoming = Array.isArray(rows) ? rows : []
  const previous = subTables[key]
  const next =
    sharedKeys.has(key) && Array.isArray(previous)
      ? mergeSubTableRowsByRowId(
          previous as Record<string, unknown>[],
          incoming as Record<string, unknown>[],
          binding.primaryKeyFields,
        )
      : incoming
  subTables[key] = next
  subTableData[key] = next as Array<Record<string, unknown>>
  return key
}

export function rowKeyForScope(
  row: Record<string, unknown>,
  pkFields: string[] | null | undefined,
): Record<string, unknown> | null {
  const pk = (pkFields ?? []).map(f => String(f).trim()).filter(Boolean)
  if (pk.length > 0) {
    const out: Record<string, unknown> = {}
    for (const field of pk) {
      const v = row[field] ?? nestedRowKeyValue(row, field)
      if (v === undefined || v === null || String(v).trim() === '') return null
      out[field] = v
    }
    return out
  }
  const uuid = row[PLATFORM_ROW_UUID_FIELD]
  if (uuid == null || String(uuid).trim() === '') return null
  return { [PLATFORM_ROW_UUID_FIELD]: uuid }
}

export function buildBindingScope(
  binding: CanonicalStampBinding,
  rows: unknown[],
  emptied: boolean,
  baselineRows?: unknown[],
): SubTableBindingScope | null {
  const storeKey = subTableStoreKey(binding)
  const bindingId = binding.bindingId
  if (!storeKey || bindingId == null || String(bindingId).trim() === '') return null
  const pk = Array.isArray(binding.primaryKeyFields) ? binding.primaryKeyFields : null
  const rowKeys: Array<Record<string, unknown>> = []
  for (const raw of Array.isArray(rows) ? rows : []) {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) continue
    const key = rowKeyForScope(raw as Record<string, unknown>, pk)
    if (key) rowKeys.push(key)
  }
  const deletedRows: Array<Record<string, unknown>> = []
  for (const raw of baselineRows ?? []) {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) continue
    const row = raw as Record<string, unknown>
    const key = rowKeyForScope(row, pk)
    if (!key) throw new Error('Saved sub-table row has no complete identity')
    const remains = rowKeys.some(candidate => Object.entries(key).every(
      ([field, value]) => String(candidate[field]) === String(value),
    ))
    if (!remains) deletedRows.push({ ...key, _wsRowVersion: row._wsRowVersion ?? 0 })
  }
  return {
    bindingId: String(bindingId),
    storeKey,
    rowKeys,
    emptied,
    ...(deletedRows.length ? { deletedRows } : {}),
  }
}

function nestedRowKeyValue(row: Record<string, unknown>, field: string): unknown {
  const rk = row.rowKey
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    return (rk as Record<string, unknown>)[field]
  }
  return undefined
}

/** Deletions are computed against the original read snapshot, never the mutable widget store. */
export function buildBindingScopeFromBaseline(
  binding: CanonicalStampBinding & FilterProjectionBinding,
  rows: unknown[],
  emptied: boolean,
  baseline: Record<string, unknown>,
  siblings: readonly FilterProjectionBinding[],
  form: FilterProjectionFormContext,
): SubTableBindingScope | null {
  const before = readSubTableRows(baseline, binding) ?? []
  const visibleBefore = projectSavedRowsForBinding(before, binding, siblings, form) ?? before
  return buildBindingScope(binding, rows, emptied, visibleBefore)
}
