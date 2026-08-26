import type { FilePreviewKind } from './filePreviewKinds'
import { fileExtension, isBlockedPreviewExtension, kindFromExtension, kindFromMime } from './filePreviewKinds'
import { confirmPreviewKind } from './filePreviewMagic'
import { isCannotDownload, uploadPropsBlockDownload } from './filePreviewFlags'

export type { FilePreviewKind } from './filePreviewKinds'
export { fileExtension, kindFromExtension } from './filePreviewKinds'
export { isCannotDownload, uploadPropsBlockDownload } from './filePreviewFlags'
export { TEXT_CHAR_LIMIT, decodeTextPreview } from './filePreviewText'
export { extractDocPreviewText } from './filePreviewDoc'
export {
  TABLE_MAX_ROWS,
  TABLE_MAX_COLS,
  boundSpreadsheetMatrix,
  parseSpreadsheetPreview,
} from './filePreviewSpreadsheet'

const SNIFF_BYTES = 131072

/**
 * Classify a file for Portal preview. Extension selects a parser; magic bytes
 * must agree (fail closed). Missing bytes = extension/mime only (tests / fallback).
 */
export function resolveFilePreviewKind(
  name: string,
  mime?: string,
  bytes?: Uint8Array,
): FilePreviewKind {
  const ext = fileExtension(name)
  let kind = kindFromExtension(ext)
  if (kind === 'unsupported' && !isBlockedPreviewExtension(ext)) {
    kind = kindFromMime(mime || '') ?? 'unsupported'
  }
  return confirmPreviewKind(kind, bytes)
}

export async function classifyBlobPreview(name: string, blob: Blob): Promise<FilePreviewKind> {
  const prefix = new Uint8Array(await blob.slice(0, SNIFF_BYTES).arrayBuffer())
  return resolveFilePreviewKind(name, blob.type, prefix)
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
    const response = await fetch(url, { credentials: 'include' })
    if (!response.ok) {
      return { ok: false, result: response.status === 404 ? 'not-found' : 'failed' }
    }
    return { ok: true, blob: await response.blob() }
  } catch {
    return { ok: false, result: 'failed' }
  }
}
