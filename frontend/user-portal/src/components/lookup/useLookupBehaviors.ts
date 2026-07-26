// Derived auto-fill + cascade filtering between two LOOKUP columns (user-portal).
// Pure filter/cycle: @platform-shared/lookupCascadeCore. Resolve uses portal relationTableApi.
import { relationTableApi, type LookupConfig } from '@/api/relationTable'
import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'
import {
  buildDerivedFilterConditions as buildDerivedFilterConditionsCore,
  hasCascadeCycle as hasCascadeCycleCore,
  type LookupCascadeConfigLike,
} from '@platform-shared/lookupCascadeCore'

export interface FieldLike {
  fieldName: string
  dataType: string
  lookupConfig?: LookupConfig
}

export function buildDerivedFilterConditions(
  baseConditions: LookupFilterCondition[],
  cfg: LookupConfig | undefined,
  parentRow: Record<string, any> | null | undefined,
): LookupFilterCondition[] {
  return buildDerivedFilterConditionsCore(
    baseConditions,
    cfg as LookupCascadeConfigLike | undefined,
    parentRow,
  ) as LookupFilterCondition[]
}

export function hasCascadeCycle(startField: string, fields: FieldLike[]): boolean {
  return hasCascadeCycleCore(startField, fields)
}

const LOOKUP_PAGE_SIZE = 200

export async function resolveDerivedLookup(
  fieldB: FieldLike,
  parentRow: Record<string, any> | null | undefined,
  allFields: FieldLike[],
): Promise<{
  skip?: boolean
  value?: any
  /** Full matched row (single-select) — Portal forms store row objects for display. */
  row?: Record<string, any> | null
  /** Full matched rows (multi-select). */
  rows?: Record<string, any>[]
}> {
  const cfg = fieldB.lookupConfig
  const df = cfg?.derivedFrom
  const refTableId = cfg?.refTableId || cfg?.tableId
  if (!cfg || !df || !refTableId) return { skip: true }
  if (df.derivedMode === 'filter') return { skip: true }
  if (hasCascadeCycle(fieldB.fieldName, allFields)) {
    console.warn(`[lookup] cascade cycle detected on "${fieldB.fieldName}"; skipping derived auto-fill`)
    return { skip: true }
  }
  if (!parentRow) return { value: cfg.multiple ? [] : null, row: null, rows: [] }

  const conditions = buildDerivedFilterConditions(cfg.filterConditions || [], cfg, parentRow)
  const pkField = String(cfg.searchFields?.[0] || 'id').trim() || 'id'
  try {
    const res = await relationTableApi.searchForLookup(refTableId, {
      keyword: '',
      searchFields: cfg.searchFields || [],
      displayField: cfg.displayFields?.[0] || '',
      filterConditions: conditions,
      limit: cfg.multiple ? LOOKUP_PAGE_SIZE : 1,
    })
    const rows = (res.data || []) as Record<string, any>[]
    if (cfg.multiple) {
      return {
        value: rows.map(r => r[pkField] ?? r.id),
        rows,
      }
    }
    const first = rows[0]
    return {
      value: first ? (first[pkField] ?? first.id) : null,
      row: first ?? null,
    }
  } catch (e) {
    console.warn('[lookup] derived resolve failed:', e)
    return { skip: true }
  }
}

/**
 * Format a grid cell value for display. Vue's toDisplayString JSON.stringifies
 * arrays (e.g. multi LOOKUP → `[ "a", "b" ]`); join with commas instead.
 * Mirrors admin-center `lookupHelpers.formatRelationCellDisplay`.
 */
export function formatRelationCellDisplay(val: unknown): string {
  if (val == null || val === '') return ''
  if (Array.isArray(val)) {
    return val
      .map(v => (v == null ? '' : String(v).trim()))
      .filter(s => s !== '')
      .join(', ')
  }
  if (typeof val === 'string') {
    const t = val.trim()
    if (t.startsWith('[') && t.endsWith(']')) {
      try {
        const parsed = JSON.parse(t) as unknown
        if (Array.isArray(parsed)) return formatRelationCellDisplay(parsed)
      } catch {
        // keep raw string
      }
    }
    return val
  }
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

export function normalizeLookupValueForSave(value: any, cfg: LookupConfig | undefined): any {
  const pkField = String(cfg?.searchFields?.[0] || 'id').trim() || 'id'
  const toPk = (v: any): any => {
    if (v == null) return v
    if (typeof v === 'object' && !Array.isArray(v)) return v[pkField] ?? v.id ?? null
    return v
  }
  if (cfg?.multiple) {
    if (Array.isArray(value)) return value.map(toPk).filter((v: any) => v != null && String(v).trim() !== '')
    if (value == null || value === '') return []
    return [toPk(value)].filter((v: any) => v != null)
  }
  return toPk(value)
}
