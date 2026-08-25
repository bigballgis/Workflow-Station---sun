/**
 * Shared helpers for SnapshotDiffRenderer — extracted so they can be
 * imported by both the Vue component and property tests.
 */
import type { FormField, FormTab } from './formRendererHelpers'
import {
  flattenAllFormFieldSegments,
  isDisplayOnlyLayoutField,
} from './formRendererHelpers'
import { resolveLookupCellTagText } from './subTableAddDialogHelpers/lookup'
import { unwrapUserLikeValueToDisplayString } from './subTableAddDialogHelpers/userDisplay'
import { isStoredFileUrl } from './subTableAddDialogHelpers/fileColumns'
import { fileDisplayText } from '@/utils/mainTableViewCsvExport'

export interface DiffRow {
  key: string
  label: string
  snapshotValue: unknown
  liveValue: unknown
  changed: boolean
}

const SKIP_SNAPSHOT_TYPES = new Set([
  'subTable',
  'inlineSubForm',
  'recordNote',
  'miAssignment',
  'row',
  'col',
  'card',
  'tabs',
  'collapse',
  'space',
])

/** Lookup / dictionary rows store the whole record; prefer the designed label over raw JSON. */
const DICTIONARY_DISPLAY_KEYS = [
  'dropdown_name',
  'status_name',
  'stage_name',
  'standardizations',
  'objectives',
  'label',
  'name',
  'title',
] as const

type LookupFormField = FormField & {
  _lookupSelectedDisplayField?: string
  _lookupDisplayField?: string
  _lookupDisplayFields?: string[]
}

function isSnapshotDiffField(field: FormField): boolean {
  if (isDisplayOnlyLayoutField(field)) return false
  if (SKIP_SNAPSHOT_TYPES.has(field.type)) return false
  const key = field.key != null ? String(field.key) : ''
  if (!key || key.startsWith('__')) return false
  return true
}

/**
 * Main-form data fields only. Layout widgets and {@code __subTable_*} placeholders
 * are omitted here; frozen sub-table rows render as separate labeled grids.
 */
export function collectSnapshotDiffFields(
  fields: FormField[],
  tabs?: FormTab[],
  fieldsAfterTabs?: FormField[],
): FormField[] {
  return flattenAllFormFieldSegments(fields, tabs, fieldsAfterTabs)
    .filter(isSnapshotDiffField)
}

function lookupDisplayProps(field?: FormField) {
  if (!field) return null
  const f = field as LookupFormField
  return {
    selectedDisplayField: f._lookupSelectedDisplayField,
    displayField: f._lookupDisplayField,
    displayFields: f._lookupDisplayFields,
  }
}

function formatObjectValue(value: Record<string, unknown>, field?: FormField): string {
  if (field?.type === 'lookup') {
    const lookupText = resolveLookupCellTagText(lookupDisplayProps(field), value)
    if (lookupText && lookupText !== '-') return lookupText
  }
  for (const key of DICTIONARY_DISPLAY_KEYS) {
    const raw = value[key]
    if (raw != null && typeof raw !== 'object') {
      const s = String(raw).trim()
      if (s) return s
    }
  }
  return unwrapUserLikeValueToDisplayString(value)
}

/** Same filename rule as Change History / CSV export: originalName, else last path segment. */
function formatStoredText(value: string): string {
  return isStoredFileUrl(value) ? fileDisplayText(value) : value
}

/** Human-readable snapshot cell text (never raw JSON for lookup / dictionary rows). */
export function formatSnapshotDisplayValue(value: unknown, field?: FormField): string {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') {
    if (Array.isArray(value)) {
      if (value.length === 0) return '-'
      return value.map(item => formatSnapshotDisplayValue(item, field)).join(', ')
    }
    return formatStoredText(formatObjectValue(value as Record<string, unknown>, field))
  }
  return formatStoredText(String(value))
}

/**
 * Compute diff rows from snapshot and live values.
 * Pure function for property testing.
 */
export function computeDiffRows(
  snapshotValues: Record<string, unknown>,
  liveValues: Record<string, unknown>,
  fields: FormField[],
  tabs?: FormTab[],
  fieldsAfterTabs?: FormField[],
): DiffRow[] {
  return collectSnapshotDiffFields(fields, tabs, fieldsAfterTabs)
    .map(field => {
      const sv = snapshotValues[field.key]
      const lv = liveValues[field.key]
      return {
        key: field.key,
        label: field.label || field.key,
        snapshotValue: sv,
        liveValue: lv,
        changed: JSON.stringify(sv) !== JSON.stringify(lv),
      }
    })
}
