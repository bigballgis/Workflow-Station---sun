// Derived auto-fill + cascade filtering between two LOOKUP columns (admin-center).
// Pure filter/cycle: @platform-shared/lookupCascadeCore. Resolve uses relationTableDataApi.
import { relationTableDataApi, type LookupConfig, type LookupFilterCondition } from '@/api/relationTable'
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

/** Build B-table filter conditions from the parent lookup's selected row. */
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

/**
 * Detect a cascade cycle: does following parentField links from `startField`
 * ever return to `startField`? Guards against A⇐B and B⇐A looping forever.
 */
export function hasCascadeCycle(startField: string, fields: FieldLike[]): boolean {
  return hasCascadeCycleCore(startField, fields)
}

const LOOKUP_PAGE_SIZE = 200

/**
 * Resolve a dependent lookup B after its parent A was selected.
 * Returns the PK value(s) to write into formData[B.fieldName]:
 *   - B multiple → array of PKs of all matched rows
 *   - B single   → the first matched row's PK (or null)
 * When derivedMode === 'filter', returns { skip: true } (caller only narrows candidates).
 */
export async function resolveDerivedLookup(
  fieldB: FieldLike,
  parentRow: Record<string, any> | null | undefined,
  allFields: FieldLike[],
): Promise<{ skip?: boolean; value?: any } > {
  const cfg = fieldB.lookupConfig
  const df = cfg?.derivedFrom
  if (!cfg || !df || !cfg.refTableId) return { skip: true }
  if (df.derivedMode === 'filter') return { skip: true }
  if (hasCascadeCycle(fieldB.fieldName, allFields)) {
    console.warn(`[lookup] cascade cycle detected on "${fieldB.fieldName}"; skipping derived auto-fill`)
    return { skip: true }
  }
  if (!parentRow) return { value: cfg.multiple ? [] : null }

  const conditions = buildDerivedFilterConditions(cfg.filterConditions || [], cfg, parentRow)
  const pkField = String(cfg.searchFields?.[0] || 'id').trim() || 'id'
  try {
    const rows = await relationTableDataApi.searchForLookup(cfg.refTableId, {
      keyword: '',
      searchFields: cfg.searchFields || [],
      displayField: cfg.displayFields?.[0] || '',
      filterConditions: conditions,
      limit: cfg.multiple ? LOOKUP_PAGE_SIZE : 1,
    }) || []
    if (cfg.multiple) {
      return { value: rows.map(r => (r as Record<string, any>)[pkField] ?? (r as Record<string, any>).id) }
    }
    const first = rows[0] as Record<string, any> | undefined
    return { value: first ? (first[pkField] ?? first.id) : null }
  } catch (e) {
    console.warn('[lookup] derived resolve failed:', e)
    return { skip: true }
  }
}

/** Normalize a LookupField value (row object / scalar / array) into the stored PK form. */
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
