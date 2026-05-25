import type { DialogColumn } from './subTableAddDialogHelpers'

export function isStoredFileUrl(value: unknown): value is string {
  if (typeof value !== 'string' || !value.trim()) return false
  return /\/upload\/files\//i.test(value.trim())
}

export function isLikelyFileStorageFieldName(fieldName: string): boolean {
  const f = String(fieldName || '').trim().toLowerCase()
  if (!f) return false
  if (f === 'file_name' || f === 'filename' || f.endsWith('_name')) return false
  return f === 'file' || f === 'fileupload' || f === 'attachment' || f.endsWith('_file')
}

export function isUploadColumn(
  col: Pick<DialogColumn, 'type' | 'field'>,
  cellValue?: unknown,
): boolean {
  if (col.type === 'upload') return true
  if (isStoredFileUrl(cellValue)) return true
  return isLikelyFileStorageFieldName(col.field)
}

export function getFilenameFromUrl(url: string, savedName?: string): string {
  if (savedName) return savedName
  if (!url) return 'unknown file'
  try {
    const parsed = new URL(url, window.location.origin)
    const fromQuery = parsed.searchParams.get('originalName')
      || parsed.searchParams.get('fileName')
      || parsed.searchParams.get('filename')
      || parsed.searchParams.get('name')
    if (fromQuery) return decodeURIComponent(fromQuery)
    const pathPart = parsed.pathname.split('/').pop()
    return pathPart || 'unknown file'
  } catch {
    const queryStr = String(url).includes('?') ? String(url).split('?')[1] : ''
    if (queryStr) {
      const params = new URLSearchParams(queryStr)
      const fromQuery = params.get('originalName') || params.get('fileName')
      if (fromQuery) {
        try {
          return decodeURIComponent(fromQuery)
        } catch {
          return fromQuery
        }
      }
    }
    const [pathPart] = String(url).split('?')
    return pathPart.split('/').pop() || 'unknown file'
  }
}

/** Ensure list/preview columns use type upload when field or sample values indicate FILE storage. */
export function normalizeSubTableColumns<T extends { field: string; type?: string; label?: string; minWidth?: number; props?: Record<string, unknown> }>(
  columns: T[],
  sampleRows?: Array<Record<string, unknown>>,
): T[] {
  const row0 = sampleRows?.[0]
  return columns.map((col) => {
    if (col.type === 'upload') return col
    const sample = row0?.[col.field]
    if (!isUploadColumn(col, sample)) return col
    const props = { ...(col.props || {}), action: '/api/v1/upload' }
    return {
      ...col,
      type: 'upload',
      minWidth: col.minWidth && col.minWidth >= 180 ? col.minWidth : 180,
      props,
    }
  })
}

export function resolveFileFetchUrl(url: string): string {
  const trimmed = String(url || '').trim()
  if (!trimmed) return trimmed
  if (/^https?:\/\//i.test(trimmed)) return trimmed
  if (trimmed.startsWith('/')) return `${window.location.origin}${trimmed}`
  return trimmed
}
