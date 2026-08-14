/**
 * Resolving saved sub-table rows for a binding from process variables ({@code __subTables__}),
 * primary-key field resolution and variable-map coercion helpers.
 */

import { legacyBindingIdAliases } from '@/components/formRendererHelpers'
import { normalizeSubTableName } from './subTableCore'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import { isMiDashboardSubTableBinding } from './subTableBindingKinds'
import { mergeAllSlicesForSharedProcessSubTableBinding } from './subTableSliceMerge'

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
  forbidNameFallback?: boolean
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
  // {@link forbidNameFallback} only disables table-name slice keys — not numeric id union.
  if (!isMiDashboardSubTableBinding(binding)) return rows
  if (opts?.mergeSiblingSlices === false) return rows
  const rtMap = opts?.bindingTableById ?? new Map<number, number | null>()
  const allSlices = mergeAllSlicesForSharedProcessSubTableBinding(savedSubTables, binding, rtMap)
  return mergeSubTableRowsByRowId(rows, allSlices, binding.primaryKeyFields ?? null)
}

function mergeTableNameSlicesInto(
  rows: any[],
  savedSubTables: Record<string, unknown>,
  binding: ResolveSubTableRowsBinding,
): any[] {
  const seen = new Set<unknown>([rows])
  let merged = rows
  const nameKeys = [
    binding.tableName,
    binding.physicalTableName,
    binding.tableName ? normalizeSubTableName(binding.tableName) : '',
    binding.physicalTableName ? normalizeSubTableName(binding.physicalTableName) : '',
  ].filter((k): k is string => typeof k === 'string' && k.trim().length > 0)
  for (const key of nameKeys) {
    const extra = savedSubTables[key] ?? savedSubTables[String(key)]
    if (!Array.isArray(extra) || extra.length === 0 || seen.has(extra)) continue
    seen.add(extra)
    merged = mergeSubTableRowsByRowId(merged, extra as any[], binding.primaryKeyFields ?? null)
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
  let merged = rows
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
  return merged
}

export function resolveSubTableRowsForBinding(
  savedSubTables: Record<string, unknown> | null | undefined,
  binding: ResolveSubTableRowsBinding,
  opts?: ResolveSubTableRowsOpts,
): any[] | undefined {
  if (!savedSubTables || typeof savedSubTables !== 'object') return undefined

  const tryKey = (key: string | number): any[] | undefined => {
    const v = savedSubTables[key] ?? savedSubTables[String(key)]
    return Array.isArray(v) && v.length > 0 ? (v as any[]) : undefined
  }

  const finish = (found: any[]): any[] => {
    // Overlay same-table-id sibling slices (leftover binding ids), then the
    // table-name alias last so form-below-table Save (Y) wins on reopen.
    // Own-slice-last would hide sibling Y when the current key is a leftover N.
    let merged = mergeSameTableIdNumericSlicesInto(
      found,
      savedSubTables,
      binding,
      opts?.bindingTableById,
    )
    if (!opts?.forbidNameFallback) {
      merged = mergeTableNameSlicesInto(merged, savedSubTables, binding)
    }
    return enrichMiDashboardResolvedRows(merged, savedSubTables, binding, opts)
  }

  let rows: any[] | undefined
  for (const alias of legacyBindingIdAliases(binding.bindingId)) {
    rows = tryKey(alias)
    if (rows) return finish(rows)
  }

  if (!opts?.forbidNameFallback) {
    const nameKeys = [
      binding.tableName,
      binding.physicalTableName,
      binding.tableName ? normalizeSubTableName(binding.tableName) : '',
      binding.physicalTableName ? normalizeSubTableName(binding.physicalTableName) : '',
    ].filter((k): k is string => typeof k === 'string' && k.trim().length > 0)
    for (const key of nameKeys) {
      rows = tryKey(key)
      if (rows) return enrichMiDashboardResolvedRows(rows, savedSubTables, binding, opts)
    }
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
        if (rows) return enrichMiDashboardResolvedRows(rows, savedSubTables, binding, opts)
      }
    }
  }

  if (opts?.mergeSiblingSlices !== false && isMiDashboardSubTableBinding(binding)) {
    const merged = mergeAllSubTableSlicesFromVariables(savedSubTables, binding.primaryKeyFields ?? null)
    if (merged.length > 0) return merged
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
  forbidNameFallback = false,
  bindingTableById?: Map<number, number | null>,
): any[] | undefined {
  return resolveSubTableRowsForBinding(subTables, binding, {
    forbidNameFallback,
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
