import { extractFileLinks, fileDisplayText, type FileLink } from '../list/fileNames'

/** Default max files when the designer has not set {@code maxFiles}. */
export const DEFAULT_UPLOAD_MAX_FILES = 10

/** Hard cap for the designer number input. */
export const ABSOLUTE_UPLOAD_MAX_FILES = 50

export interface StoredUploadFile {
  url: string
  name: string
}

/**
 * Resolve how many files an Upload/FILE field accepts.
 * Old generator wrote {@code multiple:false} + {@code limit:1}; that is not designer intent.
 * Explicit {@code maxFiles} (this feature) wins. Else {@code multiple:true} + {@code limit}.
 */
export function resolveUploadMaxFiles(props?: Record<string, unknown> | null): number {
  const maxFiles = props?.maxFiles
  if (isPositiveInt(maxFiles)) return clampMaxFiles(maxFiles)
  if (props?.multiple === true && isPositiveInt(props.limit)) return clampMaxFiles(props.limit)
  return DEFAULT_UPLOAD_MAX_FILES
}

export function isUploadMultiple(props?: Record<string, unknown> | null): boolean {
  return resolveUploadMaxFiles(props) > 1
}

export function persistUploadValue(
  files: Array<{ url: string; name: string }>,
  maxFiles: number,
): string | StoredUploadFile[] {
  const trimmed = files
    .filter((f) => typeof f.url === 'string' && f.url.trim())
    .slice(0, clampMaxFiles(maxFiles))
    .map((f) => ({ url: f.url.trim(), name: f.name?.trim() || fileDisplayText(f.url) }))
  if (maxFiles <= 1) return trimmed[0]?.url ?? ''
  return trimmed
}

export function persistFromUploadFileList(
  fileList: Array<{ url?: string; name?: string; status?: string; response?: unknown }>,
  maxFiles: number,
): string | StoredUploadFile[] {
  const files: StoredUploadFile[] = []
  for (const item of fileList) {
    if (item.status && item.status !== 'success') continue
    const url = extractStoredUploadUrl(item.response) || String(item.url || '').trim()
    if (!url) continue
    const name = (typeof item.name === 'string' && item.name.trim()) || fileDisplayText(url)
    files.push({ url, name })
  }
  return persistUploadValue(files, maxFiles)
}

export function isInflightUploadStatus(status?: string): boolean {
  return Boolean(status && status !== 'success')
}

/**
 * Persist only finished files, but keep the live el-upload rows (including uploading)
 * so the first success cannot wipe the rest of a multi-file batch.
 */
export function splitUploadFileList<T extends {
  url?: string
  name?: string
  status?: string
  response?: unknown
}>(
  liveList: T[],
  maxFiles: number,
): { stored: string | StoredUploadFile[]; display: T[] } {
  const stored = persistFromUploadFileList(liveList, maxFiles)
  const storedUrls = new Set(extractFileLinks(stored).map((link) => link.url))
  const display = liveList.filter((item) => {
    if (isInflightUploadStatus(item.status)) return true
    const url = extractStoredUploadUrl(item.response) || String(item.url || '').trim()
    return Boolean(url && storedUrls.has(url))
  })
  return { stored, display }
}

export function joinTargetFileNames(files: Array<{ name: string }>): string {
  return files.map((f) => f.name).filter(Boolean).join('; ')
}

/** Stable key of stored URLs so hydrate can skip rebuilding an unchanged file list. */
export function uploadValueFingerprint(value: unknown): string {
  return extractFileLinks(value).map((l) => l.url).join('\0')
}

export function formatUploadCellText(value: unknown): { text: string; count: number; links: FileLink[] } {
  const links = extractFileLinks(value)
  if (links.length === 0) return { text: '', count: 0, links }
  if (links.length === 1) return { text: links[0].name, count: 1, links }
  return { text: `${links[0].name} +${links.length - 1}`, count: links.length, links }
}

export function toElUploadFileList(value: unknown): Array<{ name: string; url: string; status: 'success' }> {
  return extractFileLinks(value).map((link) => ({
    name: link.name,
    url: link.url,
    status: 'success' as const,
  }))
}

/** Parse upload API body (ApiResponse wrapper or inner payload) to a stored file URL. */
export function extractStoredUploadUrl(res: unknown): string {
  if (res == null) return ''
  if (typeof res === 'string') return res.trim()
  if (typeof res !== 'object') return ''
  const o = res as Record<string, unknown>
  const direct = o.url ?? o.fileUrl ?? o.file_url ?? o.filePath ?? o.file_path
  if (typeof direct === 'string' && direct.trim()) return direct.trim()
  if (o.data != null && o.data !== o) return extractStoredUploadUrl(o.data)
  if (o.response != null && o.response !== o) return extractStoredUploadUrl(o.response)
  return ''
}

function isPositiveInt(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 1
}

function clampMaxFiles(value: number): number {
  return Math.min(Math.floor(value), ABSOLUTE_UPLOAD_MAX_FILES)
}
