export type FilePreviewKind = 'image' | 'pdf' | 'text' | 'unsupported'

const IMAGE_EXT = /\.(jpe?g|png|gif|webp|bmp)(?:$|\?)/i
const PDF_EXT = /\.pdf(?:$|\?)/i
const TEXT_EXT = /\.(txt|csv|log|md)(?:$|\?)/i

/** True only when the designer switch is explicitly on; missing/legacy = download allowed. */
export function isCannotDownload(value: unknown): boolean {
  return value === true || value === 'true' || value === 1
}

/** Designer switch `cannotDownload` and form-create native `canNotDownload`. */
export function uploadPropsBlockDownload(props: Record<string, unknown> | null | undefined): boolean {
  if (!props) return false
  return isCannotDownload(props.cannotDownload) || isCannotDownload(props.canNotDownload)
}

export function resolveFilePreviewKind(name: string, mime?: string): FilePreviewKind {
  const type = (mime || '').split(';')[0].trim().toLowerCase()
  if (type.startsWith('image/') && !type.includes('svg')) return 'image'
  if (type === 'application/pdf') return 'pdf'
  if (type === 'text/plain' || type === 'text/csv' || type === 'text/markdown') return 'text'
  const source = name || ''
  if (IMAGE_EXT.test(source)) return 'image'
  if (PDF_EXT.test(source)) return 'pdf'
  if (TEXT_EXT.test(source)) return 'text'
  return 'unsupported'
}

export function triggerBlobDownload(blob: Blob, filename: string): void {
  const blobUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = blobUrl
  a.download = filename || 'download'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(blobUrl)
}

export type StoredFileDownloadResult = 'ok' | 'not-found' | 'failed'

export async function fetchStoredFileBlob(url: string): Promise<{ ok: true; blob: Blob } | { ok: false; result: StoredFileDownloadResult }> {
  try {
    const response = await fetch(url)
    if (!response.ok) {
      return { ok: false, result: response.status === 404 ? 'not-found' : 'failed' }
    }
    return { ok: true, blob: await response.blob() }
  } catch {
    return { ok: false, result: 'failed' }
  }
}
