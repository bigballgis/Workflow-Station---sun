import type { MainTableViewFieldColumn, MainTableViewDataRow } from '@/api/mainTableView'
import { fileDisplayText, isUploadUrl } from '@platform-shared/list/fileNames'

export { extractFileLinks, fileDisplayText, type FileLink } from '@platform-shared/list/fileNames'

export function csvEscape(value: string): string {
  if (/[",\n\r]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`
  }
  return value
}

// Preferred display keys for a lookup/FK object value, in priority order.
const LOOKUP_DISPLAY_KEYS = ['name', 'displayName', 'display_name', 'label', 'title', 'text']

/** Extract a human-readable label from a lookup/FK object value (e.g. {id,name,...} → "name"). */
export function lookupDisplayText(obj: Record<string, unknown>): string {
  for (const key of LOOKUP_DISPLAY_KEYS) {
    const v = obj[key]
    if (v != null && typeof v !== 'object' && String(v).trim() !== '') return String(v)
  }
  // Fall back to the first non-id primitive string value.
  for (const [k, v] of Object.entries(obj)) {
    if (k === 'id' || k.endsWith('Id') || k.endsWith('_id')) continue
    if (v != null && typeof v !== 'object' && String(v).trim() !== '') return String(v)
  }
  // Last resort: the id itself.
  const id = obj.id
  return id != null ? String(id) : ''
}

export function formatMainTableViewCell(value: unknown): string {
  if (value == null) return '-'
  if (Array.isArray(value)) {
    // Multi-value lookup / multi-file: join each element's display text.
    const parts = value.map(v =>
      v != null && typeof v === 'object'
        ? lookupDisplayText(v as Record<string, unknown>)
        : String(v),
    ).filter(s => s.trim() !== '')
    return parts.length ? parts.join(', ') : '-'
  }
  if (typeof value === 'object') {
    const text = lookupDisplayText(value as Record<string, unknown>)
    return text.trim() !== '' ? text : '-'
  }
  // A bare upload path/URL → show just the filename.
  const str = String(value)
  if (isUploadUrl(str)) return fileDisplayText(str)
  return str
}

export function downloadMainTableViewRowsAsCsv(
  rows: MainTableViewDataRow[],
  columns: MainTableViewFieldColumn[],
  baseName: string,
): void {
  const header = [
    csvEscape('processInstanceId'),
    ...columns.map(col => csvEscape(col.displayLabel)),
  ].join(',')
  const lines = rows.map(row =>
    [
      csvEscape(row.processInstanceId),
      ...columns.map(col => csvEscape(formatMainTableViewCell(row.values[col.fieldName]))),
    ].join(','),
  )
  const content = `\uFEFF${[header, ...lines].join('\n')}`
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${baseName}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
