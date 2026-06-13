/**
 * Sub-table row normalization: drop vacuous rows and strict-subset "ghost" rows
 * produced by slice merging / nested flattening.
 */

import { SUB_TABLE_ROW_META_KEYS } from './internal'

function countSubstantiveSubTableRowFields(row: Record<string, unknown>): number {
  let n = 0
  for (const [k, v] of Object.entries(row)) {
    if (SUB_TABLE_ROW_META_KEYS.has(k) || k.startsWith('__')) continue
    if (v === undefined || v === null) continue
    if (typeof v === 'string' && v.trim() === '') continue
    if (typeof v === 'boolean') {
      n++
      continue
    }
    if (typeof v === 'number' && !Number.isNaN(v)) {
      n++
      continue
    }
    if (Array.isArray(v) && v.length > 0) {
      n++
      continue
    }
    if (typeof v === 'object') {
      n++
      continue
    }
    if (String(v).trim() !== '') n++
  }
  return n
}

function subTableRowFieldValuesEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (a == null || b == null) return false
  return String(a).trim() === String(b).trim()
}

/** True when every populated business field on {@code thin} matches {@code fat} (strict subset). */
function isSubTableRowSubsetOf(
  thin: Record<string, unknown>,
  fat: Record<string, unknown>,
): boolean {
  let thinPopulated = 0
  for (const [k, v] of Object.entries(thin)) {
    if (SUB_TABLE_ROW_META_KEYS.has(k) || k.startsWith('__')) continue
    if (v === undefined || v === null) continue
    if (typeof v === 'string' && v.trim() === '') continue
    thinPopulated++
    if (!subTableRowFieldValuesEqual(v, fat[k])) return false
  }
  if (thinPopulated === 0) return false
  return countSubstantiveSubTableRowFields(fat) > thinPopulated
}

/**
 * Drop ghost rows produced when {@link mergeAllSubTableSlicesFromVariables} or nested {@code __subTables__}
 * flattening pulls a thin slice (e.g. only {@code description}) into the same binding as a full row.
 */
export function dropSubsumedSubTableRows(rows: any[] | undefined | null): any[] {
  if (!Array.isArray(rows) || rows.length <= 1) return Array.isArray(rows) ? [...rows] : []
  const keep: any[] = []
  for (let i = 0; i < rows.length; i++) {
    const a = rows[i]
    if (!a || typeof a !== 'object') continue
    const aRec = a as Record<string, unknown>
    if (countSubstantiveSubTableRowFields(aRec) === 0) continue
    let subsumed = false
    for (let j = 0; j < rows.length; j++) {
      if (i === j) continue
      const b = rows[j]
      if (!b || typeof b !== 'object') continue
      if (isSubTableRowSubsetOf(aRec, b as Record<string, unknown>)) {
        subsumed = true
        break
      }
    }
    if (!subsumed) keep.push(a)
  }
  return keep
}

/** Remove vacuous rows and strict subsets before persisting or binding hydration. */
export function normalizeSubTableRowsForBinding(rows: any[] | undefined | null): any[] {
  return dropSubsumedSubTableRows(
    (Array.isArray(rows) ? rows : []).filter(r => {
      if (!r || typeof r !== 'object') return false
      return countSubstantiveSubTableRowFields(r as Record<string, unknown>) > 0
    }),
  )
}
