// Self-contained lookup helpers for admin-center Table Data LOOKUP fields.
// Ported from user-portal subTableAddDialogHelpers/lookup.ts + userDisplay.ts,
// trimmed to what LookupField/LookupViewDisplay need. admin-center and
// user-portal are separate apps with separate API clients, so these are copies
// kept API-shape-identical (values are PKs; rows are raw Maps).
import type { LookupConfig } from '@/api/relationTable'

export function parseLookupConfig(raw: unknown): Partial<LookupConfig> {
  try {
    const cfg = typeof raw === 'string' ? JSON.parse(raw || '{}') : (raw || {})
    return cfg && typeof cfg === 'object' ? (cfg as Partial<LookupConfig>) : {}
  } catch {
    return {}
  }
}

/**
 * Format a grid cell value for display. Vue's toDisplayString JSON.stringifies
 * arrays (e.g. multi LOOKUP → `[ "a", "b" ]`); join with commas instead.
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

/** snake_case user-like objects → a plain display string (mirrors portal unwrapUserLikeValueToDisplayString). */
export function unwrapUserLikeValueToDisplayString(rawValue: unknown): string {
  if (rawValue === null || rawValue === undefined) return '-'
  if (typeof rawValue !== 'object' || Array.isArray(rawValue)) {
    return String(rawValue)
  }
  const o = rawValue as Record<string, unknown>
  const preferKeys = ['full_name', 'fullName', 'displayName', 'display_name', 'name', 'username', 'email', 'label', 'title'] as const
  for (const k of preferKeys) {
    const v = o[k]
    if (v != null && typeof v !== 'object') {
      const s = String(v).trim()
      if (s && s !== '-') return s
    }
  }
  const idVal = o.id ?? (o as Record<string, unknown>).userId
  if (idVal != null && typeof idVal !== 'object') {
    const s = String(idVal).trim()
    if (s) return s
  }
  return '-'
}

interface LookupPropsLike {
  selectedDisplayField?: string
  _lookupSelectedDisplayField?: string
  displayField?: string
  displayFields?: string[]
  searchFields?: string[]
  lookupConfig?: unknown
}

export function getLookupPrimaryKeyFieldFromProps(props?: LookupPropsLike | null): string {
  const cfg = parseLookupConfig(props?.lookupConfig)
  return String(props?.searchFields?.[0] || cfg.searchFields?.[0] || 'id').trim() || 'id'
}

function pickFirstNonPrimaryDisplayField(pkField: string, candidates: Array<string | undefined | null>): string {
  for (const candidate of candidates) {
    const field = typeof candidate === 'string' ? candidate.trim() : ''
    if (field && field !== pkField) return field
  }
  return ''
}

/** selectedDisplayField → displayFields[0] → displayField → searchFields[0]. */
export function getLookupSelectedDisplayFieldFromProps(props?: LookupPropsLike | null): string {
  if (!props) return ''
  const cfg = parseLookupConfig(props.lookupConfig)
  const pkField = getLookupPrimaryKeyFieldFromProps(props)

  const explicit = [props._lookupSelectedDisplayField, props.selectedDisplayField, cfg.selectedDisplayField]
    .map(v => (typeof v === 'string' ? v.trim() : ''))
    .find(v => v !== '')
  if (explicit) return explicit

  const fromDisplayFields = pickFirstNonPrimaryDisplayField(pkField, [
    ...(Array.isArray(props.displayFields) ? props.displayFields.map(String) : []),
    ...(Array.isArray(cfg.displayFields) ? cfg.displayFields.map(String) : []),
  ])
  if (fromDisplayFields) return fromDisplayFields

  const fromDisplayField = pickFirstNonPrimaryDisplayField(pkField, [props.displayField])
  if (fromDisplayField) return fromDisplayField

  if (Array.isArray(props.displayFields) && props.displayFields.length > 0) return String(props.displayFields[0])
  if (Array.isArray(cfg.displayFields) && cfg.displayFields.length > 0) return String(cfg.displayFields[0])
  if (Array.isArray(props.searchFields) && props.searchFields.length > 0) return String(props.searchFields[0])
  if (Array.isArray(cfg.searchFields) && cfg.searchFields.length > 0) return String(cfg.searchFields[0])
  return ''
}

function lookupCellValue(row: Record<string, unknown>, field: string): unknown {
  if (!field) return undefined
  const val = row[field]
  if (val != null && String(val).trim() !== '') return val
  return undefined
}

/** Tag/cell label for a selected lookup row. */
export function resolveLookupCellTagText(
  lookupProps: LookupPropsLike | null | undefined,
  row: Record<string, unknown> | null | undefined,
): string {
  if (!row || typeof row !== 'object' || Array.isArray(row)) return '-'

  const cfg = parseLookupConfig(lookupProps?.lookupConfig)
  const pkField = getLookupPrimaryKeyFieldFromProps(lookupProps ?? null)
  const selectedField = getLookupSelectedDisplayFieldFromProps(lookupProps ?? null)

  if (selectedField) {
    const selectedVal = lookupCellValue(row, selectedField)
    if (selectedVal != null) return unwrapUserLikeValueToDisplayString(selectedVal)
  }

  const displayFields = [
    ...(Array.isArray(lookupProps?.displayFields) ? lookupProps!.displayFields! : []),
    ...(Array.isArray(cfg.displayFields) ? cfg.displayFields : []),
  ]
  for (const field of displayFields) {
    if (!field || field === pkField) continue
    const val = lookupCellValue(row, String(field))
    if (val != null) return unwrapUserLikeValueToDisplayString(val)
  }

  const displayField = pickFirstNonPrimaryDisplayField(pkField, [lookupProps?.displayField])
  if (displayField) {
    const val = lookupCellValue(row, displayField)
    if (val != null) return unwrapUserLikeValueToDisplayString(val)
  }

  if (selectedField === pkField || cfg.selectedDisplayField === pkField) {
    const pkVal = lookupCellValue(row, pkField) ?? lookupCellValue(row, 'id')
    if (pkVal != null) return unwrapUserLikeValueToDisplayString(pkVal)
  }

  return '-'
}

/** Build LookupField props from a stored LookupConfig. */
export function buildLookupFieldProps(cfg?: LookupConfig | null): Record<string, unknown> {
  const c = cfg || {}
  const selectedDisplayField = c.selectedDisplayField || c.displayFields?.[0] || ''
  return {
    tableId: c.refTableId || 0,
    searchFields: c.searchFields || [],
    displayField: c.displayFields?.[0] || '',
    displayFields: c.displayFields || [],
    selectedDisplayField,
    filterConditions: Array.isArray(c.filterConditions) ? c.filterConditions : [],
    showBackfillView: c.showBackfillView !== false,
    multiple: !!c.multiple,
    lookupConfig: JSON.stringify(c || {}),
  }
}
