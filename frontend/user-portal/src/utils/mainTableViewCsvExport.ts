import type { MainTableViewFieldColumn, MainTableViewDataRow } from '@/api/mainTableView'

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

/** Filename from an upload URL, preferring a recorded original-name query param, else the last segment. */
export function fileDisplayText(value: string): string {
  const qIdx = value.indexOf('?')
  if (qIdx >= 0) {
    const params = new URLSearchParams(value.substring(qIdx + 1))
    const original = params.get('originalName')
      || params.get('fileName')
      || params.get('filename')
      || params.get('name')
    if (original && original.trim() !== '') {
      try {
        return decodeURIComponent(original)
      } catch {
        return original
      }
    }
  }
  const path = value.substring(0, qIdx >= 0 ? qIdx : undefined).split('#')[0]
  const last = path.substring(path.lastIndexOf('/') + 1)
  try {
    return decodeURIComponent(last || value)
  } catch {
    return last || value
  }
}

/** A resolved file link extracted from a cell value: a download URL + a display filename. */
export interface FileLink {
  url: string
  name: string
}

// Matches an upload URL the portal can download from (with or without an api/vN prefix).
const UPLOAD_URL_RE = /\/(api\/v\d+\/)?upload\/files\//

function isUploadUrl(value: string): boolean {
  return UPLOAD_URL_RE.test(value)
}

/** Pull a download URL out of a string or a {url,...} object; null if it isn't an upload reference. */
function fileLinkFrom(value: unknown): FileLink | null {
  if (typeof value === 'string') {
    return isUploadUrl(value) ? { url: value, name: fileDisplayText(value) } : null
  }
  if (value && typeof value === 'object') {
    const obj = value as Record<string, unknown>
    const rawUrl = obj.url ?? obj.fileUrl ?? obj.path ?? obj.downloadUrl
    if (typeof rawUrl === 'string' && rawUrl.trim() !== '') {
      const name = typeof obj.name === 'string' && obj.name.trim() !== ''
        ? obj.name
        : fileDisplayText(rawUrl)
      return { url: rawUrl, name }
    }
  }
  return null
}

/** Extract every downloadable file link from a cell value (handles single, object, and array values). */
export function extractFileLinks(value: unknown): FileLink[] {
  if (value == null) return []
  if (Array.isArray(value)) {
    return value.map(fileLinkFrom).filter((l): l is FileLink => l !== null)
  }
  const single = fileLinkFrom(value)
  return single ? [single] : []
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
