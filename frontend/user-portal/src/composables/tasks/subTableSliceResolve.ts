/**
 * Resolving saved sub-table rows for a binding from process variables ({@code __subTables__}),
 * primary-key field resolution and variable-map coercion helpers.
 */

import { legacyBindingIdAliases } from '@/components/formRendererHelpers'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import { isMiDashboardSubTableBinding } from './subTableBindingKinds'
import { mergeAllSlicesForSharedProcessSubTableBinding } from './subTableSliceMerge'
import { rowIsSelfOwnedByStructuralFk,
  miChildFkConfigOfBinding,
} from './miLinkChildIdentity'
import { subTableStoreKey, type SubTableStoreBindingLike } from './subTableStore'

/**
 * Resolve saved rows for a binding — tolerates sibling binding-id keys and display/physical table names.
 * Assignment tasks often store MI collection rows under initiator binding id {@code 64} while the
 * Assign Task form uses binding {@code 66} for the same {@code tableId}.
 */
type ResolveSubTableRowsBinding = {
  bindingId: number
  tableName?: string
  physicalTableName?: string
  tableId?: number | null
  primaryKeyFields?: string[] | null
  columns?: Array<{ field?: string }> | null
}

type ResolveSubTableRowsOpts = {
  bindingTableById?: Map<number, number | null>
  mergeSiblingSlices?: boolean
}

/**
 * Assignment / copied-form MI collection: own binding slice (e.g. {@code 66}) is often list-only while
 * initiator slice {@code 64} carries assignee snapshots — merge before returning.
 */
function enrichMiDashboardResolvedRows(
  rows: any[],
  savedSubTables: Record<string, unknown>,
  binding: ResolveSubTableRowsBinding,
  opts?: ResolveSubTableRowsOpts,
): any[] {
  // Always merge initiator / sibling binding-id slices for MI collection grids.
  if (!isMiDashboardSubTableBinding(binding)) return rows
  if (opts?.mergeSiblingSlices === false) return rows
  const rtMap = opts?.bindingTableById ?? new Map<number, number | null>()
  const allSlices = mergeAllSlicesForSharedProcessSubTableBinding(savedSubTables, binding, rtMap)
  let merged = mergeSubTableRowsByRowId(rows, allSlices, binding.primaryKeyFields ?? null)
  /**
   * mergeAllSlicesForSharedProcessSubTableBinding ingests this binding's own slice first, then
   * every sibling sharing the same table_id, so a stale sibling copy can win the field-level merge
   * for a row this binding's own form actually owns (structural self-reference FK stamped by a
   * genuine save). Re-apply this binding's own self-owned rows last so they always win regardless
   * of how the internal ingestion order treated them.
   */
  const ownSlice = savedSubTables[binding.bindingId] ?? savedSubTables[String(binding.bindingId)]
  if (Array.isArray(ownSlice) && ownSlice.length > 0) {
    const ownSelfOwnedRows = ownSlice.filter(
      (r: any) => r && typeof r === 'object' && rowIsSelfOwnedByStructuralFk(r, binding.primaryKeyFields, miChildFkConfigOfBinding(binding as never)),
    )
    if (ownSelfOwnedRows.length > 0) {
      merged = mergeSubTableRowsByRowId(merged, ownSelfOwnedRows, binding.primaryKeyFields ?? null)
    }
  }
  return merged
}

function mergeSameTableIdNumericSlicesInto(
  rows: any[],
  savedSubTables: Record<string, unknown>,
  binding: ResolveSubTableRowsBinding,
  rtMap?: Map<number, number | null>,
): any[] {
  if (!rtMap) return rows
  const selfTid =
    binding.tableId != null && Number.isFinite(Number(binding.tableId))
      ? Number(binding.tableId)
      : (rtMap.get(Number(binding.bindingId)) ?? null)
  if (selfTid == null || !Number.isFinite(Number(selfTid))) return rows
  const tid = Number(selfTid)
  const seen = new Set<unknown>([rows])
  /**
   * `rows` is this binding's OWN slice (found by exact bindingId key above) — for a shared MI
   * table (Assign Task / Sub task / Main all reading the same Participants table), this binding
   * may be the one whose form actually owns a given row's writes, stamped by a structural
   * self-reference FK (sub_task_id === the row's own PK). Sibling slices pulled in below can hold
   * only an initialization-time copy of that same row. mergeSubTableRowsByRowId's contract is
   * "later argument wins for non-empty fields", so merging sibling data straight into `rows` let a
   * stale peer silently overwrite this binding's own current edit (e.g. To Do task detail showing
   * an old `name` after the owning sub-task saved a new one). Split `rows` into self-owned vs. not:
   * self-owned rows merge in LAST (after every sibling) so they always win; the rest keep the
   * original fold-in-order behavior.
   */
  const ownRows = rows.filter(r => r && typeof r === 'object' && rowIsSelfOwnedByStructuralFk(r, binding.primaryKeyFields, miChildFkConfigOfBinding(binding as never)))
  const restRows = rows.filter(r => !(r && typeof r === 'object' && rowIsSelfOwnedByStructuralFk(r, binding.primaryKeyFields, miChildFkConfigOfBinding(binding as never))))
  let merged = restRows
  for (const [bid, mapped] of rtMap.entries()) {
    if (legacyBindingIdAliases(binding.bindingId).includes(Number(bid))) continue
    if (mapped == null || Number(mapped) !== tid) continue
    for (const alias of legacyBindingIdAliases(bid)) {
      const extra = savedSubTables[alias] ?? savedSubTables[String(alias)]
      if (!Array.isArray(extra) || extra.length === 0 || seen.has(extra)) continue
      seen.add(extra)
      merged = mergeSubTableRowsByRowId(merged, extra as any[], binding.primaryKeyFields ?? null)
    }
  }
  if (ownRows.length > 0) {
    merged = mergeSubTableRowsByRowId(merged, ownRows, binding.primaryKeyFields ?? null)
  }
  return merged
}

export function resolveSubTableRowsForBinding(
  savedSubTables: Record<string, unknown> | null | undefined,
  binding: ResolveSubTableRowsBinding,
  opts?: ResolveSubTableRowsOpts,
): any[] | undefined {
  if (!savedSubTables || typeof savedSubTables !== 'object') return undefined

  // Canonical key first: one key per designer table (`dw:<name>` / `rt:<name>`). When present it is
  // the single source of truth — no sibling-slice overlay, no name fallback, both of which exist
  // only to paper over the legacy "one key per binding + name aliases" fan-out.
  const canonicalKey = subTableStoreKey(binding as SubTableStoreBindingLike)
  if (canonicalKey) {
    const canonical = savedSubTables[canonicalKey]
    if (Array.isArray(canonical)) return canonical as any[]
  }
  // Falling through means some writer produced a non-canonical key. The legacy chain below still
  // resolves it, so the user sees data either way — but a silent fallback is exactly how the
  // divergence this refactor removed stayed invisible for so long. Surface it in dev: a warning
  // here names a write path that still needs converging. Only warn when the fallback actually
  // finds rows, since an empty store is the normal "table has no data yet" case, not a miss.
  const warnLegacyKeyFallback = (rows: any[] | undefined) => {
    if (!import.meta.env.DEV || !rows || rows.length === 0) return rows
    console.warn(
      '[subTables] canonical key missed, resolved via legacy key — some writer is not using '
      + 'writeSubTableRows()',
      { canonicalKey, bindingId: binding.bindingId, tableName: binding.tableName,
        availableKeys: Object.keys(savedSubTables) },
    )
    return rows
  }

  const tryKey = (key: string | number): any[] | undefined => {
    const v = savedSubTables[key] ?? savedSubTables[String(key)]
    return Array.isArray(v) && v.length > 0 ? (v as any[]) : undefined
  }

  const finish = (found: any[]): any[] => {
    // Overlay same-table-id sibling slices (leftover binding ids) — never a table-name string key,
    // which several bindings sharing one logical table each overwrite independently on save.
    const merged = mergeSameTableIdNumericSlicesInto(
      found,
      savedSubTables,
      binding,
      opts?.bindingTableById,
    )
    return enrichMiDashboardResolvedRows(merged, savedSubTables, binding, opts)
  }

  let rows: any[] | undefined
  for (const alias of legacyBindingIdAliases(binding.bindingId)) {
    rows = tryKey(alias)
    if (rows) return finish(warnLegacyKeyFallback(rows)!)
  }

  const selfTid =
    binding.tableId != null && Number.isFinite(Number(binding.tableId))
      ? Number(binding.tableId)
      : null
  const rtMap = opts?.bindingTableById
  if (selfTid != null && rtMap) {
    for (const [bid, tid] of rtMap.entries()) {
      if (legacyBindingIdAliases(binding.bindingId).includes(Number(bid))) continue
      if (tid == null || Number(tid) !== selfTid) continue
      for (const alias of legacyBindingIdAliases(bid)) {
        rows = tryKey(alias)
        if (rows) {
          return enrichMiDashboardResolvedRows(
            warnLegacyKeyFallback(rows)!, savedSubTables, binding, opts)
        }
      }
    }
  }

  if (opts?.mergeSiblingSlices !== false && isMiDashboardSubTableBinding(binding)) {
    const merged = mergeAllSubTableSlicesFromVariables(savedSubTables, binding.primaryKeyFields ?? null)
    if (merged.length > 0) return warnLegacyKeyFallback(merged)!
  }

  return undefined
}

/**
 * Merge every array value under {@code variables.__subTables__} (all binding-id / label keys).
 * Backend MI overlay may live only under a sibling slice (e.g. original binding id) while the UI
 * binding for a copied form (subform_copy) reads a different key — per-binding lookup alone is stale.
 */
export function mergeAllSubTableSlicesFromVariables(
  savedSubTables: Record<string, unknown> | null | undefined,
  pkFieldNames?: string[] | null,
): any[] {
  if (!savedSubTables || typeof savedSubTables !== 'object') return []
  const seenArrays = new Set<unknown>()
  let merged: any[] = []
  for (const v of Object.values(savedSubTables)) {
    if (!Array.isArray(v) || v.length === 0 || seenArrays.has(v)) continue
    seenArrays.add(v)
    merged = mergeSubTableRowsByRowId(merged, v as any[], pkFieldNames ?? null)
  }
  return merged
}

/**
 * Prefer API {@code primaryKeyFields} (admin-center / dw_field_definitions); if missing, infer from
 * designer sub-list columns marked {@code isPrimaryKey}, then subForm rules — never a fixed column name.
 */
export function resolveSubTablePrimaryKeyFields(
  apiPrimaryKeys: string[] | undefined | null,
  bindingId: number | string | undefined | null,
  formConfig?: Record<string, any> | null
): string[] | undefined {
  const trimmed = (apiPrimaryKeys || [])
    .map(f => String(f).trim())
    .filter(Boolean)
  if (trimmed.length > 0) return trimmed
  if (bindingId == null || bindingId === '' || formConfig == null) return undefined

  const lv =
    formConfig.subListViews?.[bindingId] ?? formConfig.subListViews?.[String(bindingId)]
  const cols = lv?.columns
  if (Array.isArray(cols)) {
    const fromList = cols
      .filter(
        (c: any) =>
          c &&
          c.isPrimaryKey === true &&
          typeof c.fieldName === 'string' &&
          c.fieldName.trim().length > 0
      )
      .map((c: any) => String(c.fieldName).trim())
    if (fromList.length > 0) return fromList
  }

  const subForms = formConfig.subForms
  if (subForms && typeof subForms === 'object') {
    const sf = subForms[bindingId] ?? subForms[String(bindingId)]
    const rule = sf?.rule
    if (Array.isArray(rule)) {
      const fromRule = rule
        .filter(
          (r: any) =>
            r &&
            (r.isPrimaryKey === true || r.props?.isPrimaryKey === true) &&
            typeof r.field === 'string' &&
            r.field.trim().length > 0
        )
        .map((r: any) => String(r.field).trim())
      if (fromRule.length > 0) return fromRule
    }
  }

  return undefined
}

/**
 * `forbidNameFallback` param kept for call-site source compatibility (many callers pass
 * `ambiguous.has(binding.bindingId)` positionally) but is now UNUSED — there is no table-name
 * string-key fallback left to forbid; resolveSubTableRowsForBinding only ever resolves by exact
 * bindingId or by table_id against other bindings, never by a shared display-name key that
 * multiple bindings sharing one logical table would otherwise stomp on independently.
 */
export function getSavedSubTableRows(
  subTables: Record<string, any>,
  binding: {
    bindingId: number
    tableName: string
    physicalTableName?: string
    tableId?: number | null
    primaryKeyFields?: string[] | null
    columns?: Array<{ field?: string }> | null
  },
  _forbidNameFallback = false,
  bindingTableById?: Map<number, number | null>,
): any[] | undefined {
  return resolveSubTableRowsForBinding(subTables, binding, {
    bindingTableById,
    mergeSiblingSlices: isMiDashboardSubTableBinding(binding),
  })
}

/**
 * Some gateways / serializers deliver {@code __subTables__} as a JSON string. Without coercion,
 * portal hydration skips flatten/backfill ({@code typeof === 'object'} gate) and Link Form stays empty.
 */
export function coerceSubTablesVariableToMap(raw: unknown): Record<string, unknown> | null {
  if (raw == null) return null
  if (typeof raw === 'string') {
    const t = raw.trim()
    if (!t) return null
    try {
      const parsed = JSON.parse(t) as unknown
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>
      }
    } catch {
      return null
    }
    return null
  }
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    return raw as Record<string, unknown>
  }
  return null
}

/**
 * All {@code Record} values that are non-empty object row arrays, including slices nested under
 * {@code row.__subTables__} (Link Form persistence). Used when top-level keys alone miss child data.
 */
export function collectSubTableSliceArraysDeep(saved: Record<string, unknown>): unknown[][] {
  const out: unknown[][] = []
  const seenArrays = new WeakSet<object>()
  const walkMap = (m: Record<string, unknown>) => {
    for (const val of Object.values(m)) {
      if (!Array.isArray(val) || val.length === 0) continue
      if (seenArrays.has(val)) continue
      const row0 = val[0]
      if (!row0 || typeof row0 !== 'object') continue
      seenArrays.add(val)
      out.push(val)
      for (const row of val) {
        if (!row || typeof row !== 'object') continue
        const nest = (row as Record<string, unknown>).__subTables__
        if (nest && typeof nest === 'object' && !Array.isArray(nest)) {
          walkMap(nest as Record<string, unknown>)
        }
      }
    }
  }
  walkMap(saved)
  return out
}
