// Derived auto-fill + cascade filtering between two LOOKUP columns (user-portal).
// Mirror of admin-center/src/components/lookup/useLookupBehaviors.ts, using the
// portal relationTableApi (whose searchForLookup returns { data }).
import { relationTableApi, type LookupConfig } from '@/api/relationTable'
import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'

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
  const out: LookupFilterCondition[] = [...(baseConditions || [])]
  const df = cfg?.derivedFrom
  if (!df || !parentRow) return out
  for (const join of df.joins || []) {
    if (!join.fromColumn || !join.toColumn) continue
    const v = parentRow[join.fromColumn]
    if (v == null || String(v).trim() === '') continue
    out.push({ fieldName: join.toColumn, value: String(v), matchType: join.matchType || 'eq' })
  }
  return out
}

export function hasCascadeCycle(startField: string, fields: FieldLike[]): boolean {
  const byName = new Map(fields.map(f => [f.fieldName, f]))
  const seen = new Set<string>()
  let cur: string | undefined = byName.get(startField)?.lookupConfig?.derivedFrom?.parentField
  while (cur) {
    if (cur === startField) return true
    if (seen.has(cur)) return false
    seen.add(cur)
    cur = byName.get(cur)?.lookupConfig?.derivedFrom?.parentField
  }
  return false
}

const LOOKUP_PAGE_SIZE = 200

export async function resolveDerivedLookup(
  fieldB: FieldLike,
  parentRow: Record<string, any> | null | undefined,
  allFields: FieldLike[],
): Promise<{ skip?: boolean; value?: any }> {
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
    const res = await relationTableApi.searchForLookup(cfg.refTableId, {
      keyword: '',
      searchFields: cfg.searchFields || [],
      displayField: cfg.displayFields?.[0] || '',
      filterConditions: conditions,
      limit: cfg.multiple ? LOOKUP_PAGE_SIZE : 1,
    })
    const rows = res.data || []
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
