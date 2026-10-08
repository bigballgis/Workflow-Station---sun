import type { FilePreviewItem, FilePreviewPayload } from './useFilePreview'

/** Map of preview-window id → snapshot. One shared key would replace every open window. */
export const FILE_PREVIEW_STORAGE_KEY = 'ws-file-preview-snapshots'
const MAX_PREVIEW_SNAPSHOTS = 20
const PREVIEW_ID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

interface StoredPreview {
  savedAt: number
  payload: FilePreviewPayload
}

export function newFilePreviewId(): string {
  return crypto.randomUUID()
}

export function previewIdFromLocation(search: string): string | null {
  const id = new URLSearchParams(search).get('id')
  if (!id || !PREVIEW_ID_RE.test(id)) return null
  return id
}

export function filePreviewHref(id: string): string {
  const base = String(import.meta.env.BASE_URL || '/').replace(/\/?$/, '/')
  const path = `${base}file-preview?id=${encodeURIComponent(id)}`
  if (typeof window === 'undefined') return path
  return `${window.location.origin}${path}`
}

export function isFilePreviewRoute(): boolean {
  if (typeof window === 'undefined') return false
  return /\/file-preview\/?$/.test(window.location.pathname)
}

function isPreviewItem(value: unknown): value is FilePreviewItem {
  if (!value || typeof value !== 'object') return false
  const url = (value as FilePreviewItem).url
  return typeof url === 'string' && url.length > 0
}

export function parseFilePreviewSnapshot(raw: unknown): FilePreviewPayload | null {
  if (!raw || typeof raw !== 'object') return null
  const rec = raw as Record<string, unknown>
  if (typeof rec.url !== 'string' || rec.url.length === 0) return null
  const items = Array.isArray(rec.items) ? rec.items.filter(isPreviewItem) : undefined
  return {
    url: rec.url,
    name: typeof rec.name === 'string' ? rec.name : rec.url,
    cannotDownload: rec.cannotDownload === true,
    items,
    index: typeof rec.index === 'number' ? rec.index : undefined,
  }
}

function isStoredPreview(value: unknown): value is StoredPreview {
  if (!value || typeof value !== 'object') return false
  const rec = value as StoredPreview
  return typeof rec.savedAt === 'number' && parseFilePreviewSnapshot(rec.payload) !== null
}

function readPreviewMap(): Record<string, StoredPreview> {
  try {
    const raw = localStorage.getItem(FILE_PREVIEW_STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const map: Record<string, StoredPreview> = {}
    for (const [id, value] of Object.entries(parsed)) {
      if (isStoredPreview(value)) map[id] = value
    }
    return map
  } catch {
    // FALLBACK(ux): corrupt storage — that window hydrates empty rather than another file
    return {}
  }
}

function trimPreviewSnapshots(map: Record<string, StoredPreview>, keepId: string): void {
  const extras = Object.entries(map)
    .filter(([id]) => id !== keepId)
    .sort((a, b) => a[1].savedAt - b[1].savedAt)
  const dropCount = extras.length - (MAX_PREVIEW_SNAPSHOTS - 1)
  for (let i = 0; i < dropCount; i++) delete map[extras[i][0]]
}

export function readStoredPreviewSnapshot(id: string): FilePreviewPayload | null {
  const entry = readPreviewMap()[id]
  if (!entry) return null
  return parseFilePreviewSnapshot(entry.payload)
}

export function writeStoredPreviewSnapshot(id: string, payload: FilePreviewPayload): void {
  try {
    const map = readPreviewMap()
    map[id] = { savedAt: Date.now(), payload }
    trimPreviewSnapshots(map, id)
    localStorage.setItem(FILE_PREVIEW_STORAGE_KEY, JSON.stringify(map))
  } catch {
    // FALLBACK(ux): quota / private mode — the preview window hydrates empty
  }
}

/** `popup=yes` requests a separate browser window. Do not add `noopener`: `window.open` would return null. */
export function filePreviewWindowFeatures(): string {
  const width = 1200
  const height = 800
  const availW = window.screen?.availWidth || width
  const availH = window.screen?.availHeight || height
  const w = Math.min(width, availW)
  const h = Math.min(height, availH)
  const left = Math.max(0, Math.floor((availW - w) / 2))
  const top = Math.max(0, Math.floor((availH - h) / 2))
  return `popup=yes,width=${w},height=${h},left=${left},top=${top}`
}

export function tryOpenPreviewWindow(id: string): boolean {
  if (typeof window === 'undefined' || isFilePreviewRoute()) return false
  const opened = window.open(filePreviewHref(id), '_blank', filePreviewWindowFeatures())
  return opened != null && opened.closed !== true
}
