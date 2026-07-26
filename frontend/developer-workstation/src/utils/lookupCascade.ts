// DW LOOKUP cascade — pure filter/cycle from @platform-shared; preview helpers below.
// Runtime resolve lives in portal/admin useLookupBehaviors.ts.
import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'
import {
  buildDerivedFilterConditions as buildDerivedFilterConditionsCore,
  hasCascadeCycle as hasCascadeCycleCore,
  type LookupCascadeConfigLike,
} from '@platform-shared/lookupCascadeCore'

export interface LookupJoin {
  fromColumn: string
  toColumn: string
  matchType?: 'eq' | 'contains' | 'startsWith' | 'endsWith'
}

export interface LookupDerivedFrom {
  parentField: string
  joins: LookupJoin[]
  derivedMode: 'autofill' | 'filter'
}

export interface LookupCascadeConfig {
  filterConditions?: LookupFilterCondition[]
  derivedFrom?: LookupDerivedFrom
}

export interface SiblingLookupFieldLike {
  fieldName: string
  dataType: string
  lookupConfig?: LookupCascadeConfig
}

/** Build dependent lookup filter conditions from the parent lookup's selected row. */
export function buildDerivedFilterConditions(
  baseConditions: LookupFilterCondition[],
  cfg: LookupCascadeConfig | undefined,
  parentRow: Record<string, unknown> | null | undefined,
): LookupFilterCondition[] {
  return buildDerivedFilterConditionsCore(
    baseConditions,
    cfg as LookupCascadeConfigLike | undefined,
    parentRow,
  ) as LookupFilterCondition[]
}

/** Detect cascade cycle by following parentField links from `startField`. */
export function hasCascadeCycle(startField: string, fields: SiblingLookupFieldLike[]): boolean {
  return hasCascadeCycleCore(startField, fields)
}

/** Normalize a LOOKUP model value to a row object (or null). */
export function normalizeLookupRow(value: unknown): Record<string, unknown> | null {
  if (value == null || value === '') return null
  if (typeof value === 'object' && !Array.isArray(value)) return value as Record<string, unknown>
  return null
}

/**
 * Build a preview autofill row from parent join values (DW Form Preview mock data).
 * Real Portal autofill uses relationTableApi.searchForLookup instead.
 */
export function buildPreviewAutofillRow(
  cfg: LookupCascadeConfig | undefined,
  parentRow: Record<string, unknown> | null | undefined,
  opts?: { searchFields?: string[]; selectedDisplayField?: string; displayFields?: string[] },
): Record<string, unknown> | null {
  const df = cfg?.derivedFrom
  if (!df || df.derivedMode !== 'autofill' || !parentRow) return null
  const conditions = buildDerivedFilterConditions(cfg?.filterConditions || [], cfg, parentRow)
  if (conditions.length === 0) return null
  const row: Record<string, unknown> = {}
  for (const c of conditions) {
    if (c.fieldName) row[c.fieldName] = c.value
  }
  const pk = String(opts?.searchFields?.[0] || 'id').trim() || 'id'
  if (!(pk in row)) row[pk] = conditions[0]?.value ?? '1'
  const display = opts?.selectedDisplayField || opts?.displayFields?.[0]
  if (display && !(display in row)) {
    row[display] = 'Sample 1'
  }
  return row
}

/**
 * Autofill model value for Form Preview — Portal parity:
 * multiple LOOKUP stores full row object(s); single stores one row or null.
 */
export function buildPreviewAutofillModelValue(
  cfg: LookupCascadeConfig | undefined,
  parentRow: Record<string, unknown> | null | undefined,
  opts?: {
    searchFields?: string[]
    selectedDisplayField?: string
    displayFields?: string[]
    multiple?: boolean
  },
): Record<string, unknown> | Record<string, unknown>[] | null {
  const row = buildPreviewAutofillRow(cfg, parentRow, opts)
  if (opts?.multiple) return row ? [row] : []
  return row
}

/** Effective filter conditions for a LOOKUP cell given sibling values in the same row. */
export function effectiveLookupFilterConditionsForRow(
  baseConditions: LookupFilterCondition[],
  cfg: LookupCascadeConfig | undefined,
  row: Record<string, unknown> | null | undefined,
): LookupFilterCondition[] {
  const parent = cfg?.derivedFrom?.parentField
  if (!parent || !row) return baseConditions || []
  return buildDerivedFilterConditions(baseConditions || [], cfg, normalizeLookupRow(row[parent]))
}
