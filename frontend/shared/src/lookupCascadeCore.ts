/**
 * Pure LOOKUP cascade helpers — single source for admin-center / user-portal /
 * developer-workstation via `@platform-shared/lookupCascadeCore`.
 *
 * App-specific resolve/search stays in each app's useLookupBehaviors / preview helpers.
 * Do not duplicate buildDerivedFilterConditions / hasCascadeCycle in apps.
 */

export interface LookupFilterConditionLike {
  fieldName: string
  value: string
  matchType?: string
}

export interface LookupJoinLike {
  fromColumn: string
  toColumn: string
  matchType?: 'eq' | 'contains' | 'startsWith' | 'endsWith' | string
}

export interface LookupDerivedFromLike {
  parentField: string
  joins: LookupJoinLike[]
  derivedMode: 'autofill' | 'filter'
}

export interface LookupCascadeConfigLike {
  filterConditions?: LookupFilterConditionLike[]
  derivedFrom?: LookupDerivedFromLike
}

export interface SiblingLookupFieldLike {
  fieldName: string
  dataType?: string
  lookupConfig?: LookupCascadeConfigLike
}

/** Build dependent lookup filter conditions from the parent lookup's selected row. */
export function buildDerivedFilterConditions(
  baseConditions: LookupFilterConditionLike[],
  cfg: LookupCascadeConfigLike | undefined,
  parentRow: Record<string, unknown> | null | undefined,
): LookupFilterConditionLike[] {
  const out: LookupFilterConditionLike[] = [...(baseConditions || [])]
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

/** Detect cascade cycle by following parentField links from `startField`. */
export function hasCascadeCycle(startField: string, fields: SiblingLookupFieldLike[]): boolean {
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
