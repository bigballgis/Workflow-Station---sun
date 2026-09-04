import type { DialogColumn, ParsedLookupConfig } from './types'
import { unwrapUserLikeValueToDisplayString } from './userDisplay'

export function parseLookupConfig(raw: unknown): ParsedLookupConfig {
  try {
    const cfg = typeof raw === 'string' ? JSON.parse(raw || '{}') : (raw || {})
    return cfg && typeof cfg === 'object' ? (cfg as ParsedLookupConfig) : {}
  } catch {
    return {}
  }
}

export function getLookupPrimaryKeyFieldFromProps(props?: {
  searchFields?: string[]
  lookupConfig?: unknown
} | null): string {
  const cfg = parseLookupConfig(props?.lookupConfig)
  return String(props?.searchFields?.[0] || cfg.searchFields?.[0] || 'id').trim() || 'id'
}

function pickFirstNonPrimaryDisplayField(
  pkField: string,
  candidates: Array<string | undefined | null>,
): string {
  for (const candidate of candidates) {
    const field = typeof candidate === 'string' ? candidate.trim() : ''
    if (field && field !== pkField) return field
  }
  return ''
}

export function getLookupSelectedDisplayFieldFromProps(props?: {
  selectedDisplayField?: string
  _lookupSelectedDisplayField?: string
  displayField?: string
  displayFields?: string[]
  searchFields?: string[]
  lookupConfig?: unknown
} | null): string {
  if (!props) return ''
  const cfg = parseLookupConfig(props.lookupConfig)
  const pkField = getLookupPrimaryKeyFieldFromProps(props)

  const explicit = [
    props._lookupSelectedDisplayField,
    props.selectedDisplayField,
    cfg.selectedDisplayField,
  ]
    .map(v => (typeof v === 'string' ? v.trim() : ''))
    .find(v => v !== '')
  if (explicit) return explicit

  const fromDisplayFields = pickFirstNonPrimaryDisplayField(pkField, [
    ...(Array.isArray(props.displayFields) ? props.displayFields.map(String) : []),
    ...(Array.isArray(cfg.displayFields) ? cfg.displayFields.map(String) : []),
  ])
  if (fromDisplayFields) return fromDisplayFields

  const fromDisplayField = pickFirstNonPrimaryDisplayField(pkField, [
    cfg.displayField,
    props.displayField,
  ])
  if (fromDisplayField) return fromDisplayField

  // Last resort only when designer explicitly chose PK as the only display field.
  if (Array.isArray(props.displayFields) && props.displayFields.length > 0) {
    return String(props.displayFields[0])
  }
  if (Array.isArray(cfg.displayFields) && cfg.displayFields.length > 0) {
    return String(cfg.displayFields[0])
  }
  if (Array.isArray(props.searchFields) && props.searchFields.length > 0) {
    return String(props.searchFields[0])
  }
  if (Array.isArray(cfg.searchFields) && cfg.searchFields.length > 0) {
    return String(cfg.searchFields[0])
  }
  return ''
}

/** Same priority as LookupField / designer LookupPreview: selectedDisplayField → displayFields[0] → displayField → searchFields[0]. */
export function getLookupSelectedDisplayField(col: DialogColumn): string {
  return getLookupSelectedDisplayFieldFromProps(col.props ?? null)
}

export function buildLookupColumnProps(
  rawLookupConfig: unknown,
  options?: {
    relationViewFields?: Array<Record<string, unknown>>
    dbCfg?: {
      tableId?: number
      searchFields?: string[]
      displayField?: string
      viewFields?: unknown[]
    }
  },
): Record<string, unknown> {
  const lookupCfg = parseLookupConfig(rawLookupConfig)
  const dbCfg = options?.dbCfg
  const selectedDisplayField = lookupCfg.selectedDisplayField || lookupCfg.displayField || ''
  return {
    lookupConfig: typeof rawLookupConfig === 'string' ? rawLookupConfig : JSON.stringify(lookupCfg || {}),
    tableId: lookupCfg.tableId || dbCfg?.tableId || 0,
    searchFields: lookupCfg.searchFields || dbCfg?.searchFields || [],
    displayField: lookupCfg.displayFields?.[0] || dbCfg?.displayField || '',
    displayFields: lookupCfg.displayFields || [],
    selectedDisplayField,
    _lookupSelectedDisplayField: selectedDisplayField,
    filterConditions: Array.isArray(lookupCfg.filterConditions) ? lookupCfg.filterConditions : [],
    derivedFrom: lookupCfg.derivedFrom,
    multiple: lookupCfg.multiple === true,
    viewFields:
      lookupCfg.showBackfillView === false
        ? []
        : (options?.relationViewFields || dbCfg?.viewFields || []),
    showBackfillView: lookupCfg.showBackfillView !== false,
  }
}

function lookupCellValue(row: Record<string, unknown>, field: string): unknown {
  if (!field) return undefined
  const val = row[field]
  if (val != null && String(val).trim() !== '') return val
  return undefined
}

/**
 * When a node form has multiple:false but process variables still hold a multi LOOKUP
 * array (Start Process multiple:true), unwrap the first non-empty entry for single-select init.
 */
export function unwrapSingleLookupModelValue(val: unknown): unknown {
  if (!Array.isArray(val)) return val
  return val.find((v) => v != null && !(typeof v === 'string' && String(v).trim() === '')) ?? null
}

/** Tag/cell label for lookup rows — mirrors designer LookupPreview / LookupField. */
export function resolveLookupCellTagText(
  lookupProps: {
    selectedDisplayField?: string
    _lookupSelectedDisplayField?: string
    displayField?: string
    displayFields?: string[]
    searchFields?: string[]
    lookupConfig?: unknown
  } | null | undefined,
  row: Record<string, unknown> | null | undefined,
): string {
  if (!row || typeof row !== 'object' || Array.isArray(row)) return '-'

  const cfg = parseLookupConfig(lookupProps?.lookupConfig)
  const pkField = getLookupPrimaryKeyFieldFromProps(lookupProps ?? null)
  const selectedField = getLookupSelectedDisplayFieldFromProps(lookupProps ?? null)

  if (selectedField) {
    const selectedVal = lookupCellValue(row, selectedField)
    if (selectedVal != null) {
      return unwrapUserLikeValueToDisplayString(selectedVal)
    }
  }

  const displayFields = [
    ...(Array.isArray(lookupProps?.displayFields) ? lookupProps!.displayFields! : []),
    ...(Array.isArray(cfg.displayFields) ? cfg.displayFields : []),
  ]
  for (const field of displayFields) {
    if (!field || field === pkField) continue
    const val = lookupCellValue(row, String(field))
    if (val != null) {
      return unwrapUserLikeValueToDisplayString(val)
    }
  }

  const displayField = pickFirstNonPrimaryDisplayField(pkField, [
    lookupProps?.displayField,
    cfg.displayField,
  ])
  if (displayField) {
    const val = lookupCellValue(row, displayField)
    if (val != null) {
      return unwrapUserLikeValueToDisplayString(val)
    }
  }

  // Only show PK when designer explicitly configured it as the display field.
  if (selectedField === pkField || cfg.selectedDisplayField === pkField) {
    const pkVal = lookupCellValue(row, pkField) ?? lookupCellValue(row, 'id')
    if (pkVal != null) {
      return unwrapUserLikeValueToDisplayString(pkVal)
    }
  }

  return '-'
}

/**
 * Props that {@link buildLookupColumnProps} can only fill from the caller-resolved `options`
 * (per-binding relation view / lookup db config). Re-deriving them here from `lookupConfig` alone
 * yields empty values, so an already-resolved value on the column must win over the rebuilt blank.
 */
const CALLER_RESOLVED_LOOKUP_PROPS = ['viewFields', 'tableId', 'searchFields', 'displayField'] as const

function isBlankLookupProp(value: unknown): boolean {
  if (value == null || value === '' || value === 0) return true
  return Array.isArray(value) && value.length === 0
}

/** Merge subForm rule lookupConfig onto derived columns (list-view merges may drop selectedDisplayField). */
export function enrichLookupColumnPropsFromSubFormRule(
  columns: DialogColumn[],
  subFormRule?: unknown[] | null,
): DialogColumn[] {
  if (!Array.isArray(subFormRule) || subFormRule.length === 0) return columns
  const ruleByField = new Map<string, { type?: string; props?: Record<string, unknown> }>()
  for (const item of subFormRule) {
    if (!item || typeof item !== 'object') continue
    const field = String((item as { field?: string }).field || '').trim()
    if (field) ruleByField.set(field, item as { type?: string; props?: Record<string, unknown> })
  }
  return columns.map(col => {
    const rule = ruleByField.get(col.field)
    const rawCfg = rule?.props?.lookupConfig ?? col.props?.lookupConfig
    if (!rawCfg && col.type !== 'lookup' && rule?.type !== 'lookup') return col
    const lookupProps: Record<string, unknown> = { ...buildLookupColumnProps(rawCfg || '{}') }
    // The caller (mapSubFormRuleToDialogColumns / the list-view deriver) already resolved the
    // designed backfill view and table binding from context this function has no access to.
    // Without this, the rebuilt blanks overwrote them and the Add/Edit dialog fell back to the
    // relation table's *global* view — rendering every column instead of the designed subset.
    //
    // Only restore when the rule targets the SAME table: a rule that repoints the field at another
    // relation table must not inherit the previous table's view fields / search fields.
    const sameTable =
      isBlankLookupProp(lookupProps.tableId) ||
      isBlankLookupProp(col.props?.tableId) ||
      lookupProps.tableId === col.props?.tableId
    for (const key of CALLER_RESOLVED_LOOKUP_PROPS) {
      // showBackfillView:false deliberately yields an empty viewFields — never resurrect a stale one.
      if (key === 'viewFields' && lookupProps.showBackfillView === false) continue
      if (!sameTable && key !== 'tableId') continue
      if (isBlankLookupProp(lookupProps[key]) && !isBlankLookupProp(col.props?.[key])) {
        lookupProps[key] = col.props![key]
      }
    }
    // List-view columnType is often "field" even when the sub-form control is a lookup
    // (or an input that still carries lookupConfig). Without promoting type, SubTableField
    // skips the lookup renderer and the object falls through to the user-object fallback.
    const isLookup =
      col.type === 'lookup' || rule?.type === 'lookup' || Boolean(rawCfg)
    return {
      ...col,
      type: isLookup ? 'lookup' : col.type,
      props: { ...(col.props || {}), ...lookupProps },
    }
  })
}
