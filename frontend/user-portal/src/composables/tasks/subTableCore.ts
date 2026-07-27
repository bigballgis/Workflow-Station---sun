/**
 * Core sub-table helpers: table-name normalization, binding identity matching, row cloning.
 * Pure helpers — no reactive state (markRaw is the one Vue import: it only tags a plain object).
 */

import { markRaw } from 'vue'

export function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

/** Strip designer "ADD + …" prefix; nested {@code parentRow.__subTables__} keys often keep this label. */
export function stripLinkFormDesignerTableLabel(raw?: string): string {
  return String(raw || '').trim().replace(/^ADD\s*\+\s*/i, '').trim()
}

export function subTableBindingMatches(
  target: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null },
  source: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null }
): boolean {
  const targetPhysicalName = normalizeSubTableName(target.physicalTableName)
  const sourcePhysicalName = normalizeSubTableName(source.physicalTableName)
  if (targetPhysicalName && sourcePhysicalName && targetPhysicalName === sourcePhysicalName) return true
  const targetName = normalizeSubTableName(target.tableName)
  const sourceName = normalizeSubTableName(source.tableName)
  const samePhysicalTable = target.tableId != null && source.tableId != null && Number(target.tableId) === Number(source.tableId)
  return target.bindingId === source.bindingId || samePhysicalTable || (!!targetName && targetName === sourceName)
}

export function cloneSubTableRows(rows: any[]): any[] {
  try {
    return JSON.parse(JSON.stringify(rows))
  } catch {
    return rows.map(row => ({ ...row }))
  }
}

/** Key-order-independent serialization so two structurally equal values compare equal. */
function stableFieldValueJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableFieldValueJson).join(',')}]`
  if (value && typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
      .filter(([, v]) => v !== undefined)
      .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
      .map(([k, v]) => `${JSON.stringify(k)}:${stableFieldValueJson(v)}`)
    return `{${entries.join(',')}}`
  }
  try {
    return JSON.stringify(value) ?? 'null'
  } catch {
    return String(value)
  }
}

/**
 * Comparable key for one sub-table cell value; {@code null} when the cell carries nothing.
 *
 * LOOKUP cells hold the WHOLE selected relation-table row (an object), so identity checks that
 * used {@code String(v)} saw every lookup as {@code "[object Object]"} and identity checks that
 * skipped non-scalars ignored them entirely — two rows differing ONLY by their lookup selection
 * were treated as one and the extra row was silently dropped. Scalars keep the old trimmed-string
 * behaviour so {@code 555} and {@code "555"} still match.
 */
export function subTableFieldValueKey(value: unknown): string | null {
  if (value === undefined || value === null) return null
  if (typeof value === 'object') {
    if (Array.isArray(value)) return value.length === 0 ? null : stableFieldValueJson(value)
    return Object.keys(value as Record<string, unknown>).length === 0
      ? null
      : stableFieldValueJson(value)
  }
  const s = String(value).trim()
  return s === '' ? null : s
}

/**
 * Detach each row's nested {@code __subTables__} from the reactive graph: replace it with a
 * {@code markRaw} deep copy (empty maps dropped).
 *
 * The original guard deleted the payload outright to prevent Vue deep-reactivity freeze and
 * circular traversal when rows reference each other across slices. Deleting also destroyed the
 * sub-table-in-sub-table link: the grandchild rows live ONLY under the parent row, so the nested
 * grid rendered empty on the next task and the next save persisted a parent row without children.
 * Cloning breaks cross-slice object identity (no cycles) and markRaw keeps Vue from ever walking
 * into it, so the perf guarantee holds while the data survives.
 */
export function stripNestedSubTablesFromRows(rows: any[]): any[] {
  for (const row of rows) {
    if (!row || typeof row !== 'object' || !row.__subTables__) continue
    const detached = detachNestedSubTableMap(row.__subTables__)
    if (detached) {
      row.__subTables__ = detached
    } else {
      delete row.__subTables__
    }
  }
  return rows
}

/** Deep-copy a nested `__subTables__` map into a non-reactive value; null when it holds no rows. */
function detachNestedSubTableMap(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const out: Record<string, unknown> = {}
  let rows = 0
  for (const [key, slice] of Object.entries(value as Record<string, unknown>)) {
    if (!Array.isArray(slice) || slice.length === 0) continue
    out[key] = cloneSubTableRows(slice as any[])
    rows += slice.length
  }
  return rows > 0 ? markRaw(out) : null
}

export function cloneSubTableBindings<T extends Array<{ data: any[] }>>(bindings: T): T {
  return bindings.map(binding => ({
    ...binding,
    data: cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
  })) as T
}
