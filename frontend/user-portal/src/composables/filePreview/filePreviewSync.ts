import type { FilePreviewItem, FilePreviewPayload } from './useFilePreview'

export const FILE_PREVIEW_STORAGE_KEY = 'ws-file-preview-snapshot'
export const FILE_PREVIEW_CHANNEL = 'ws-file-preview'
export const FILE_PREVIEW_WINDOW_NAME = 'ws-file-preview'

export function filePreviewHref(): string {
  const base = String(import.meta.env.BASE_URL || '/').replace(/\/?$/, '/')
  const path = `${base}file-preview`
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

export function readStoredPreviewSnapshot(): FilePreviewPayload | null {
  try {
    const raw = localStorage.getItem(FILE_PREVIEW_STORAGE_KEY)
    if (!raw) return null
    return parseFilePreviewSnapshot(JSON.parse(raw) as unknown)
  } catch {
    return null
  }
}

export function writeStoredPreviewSnapshot(payload: FilePreviewPayload): void {
  try {
    localStorage.setItem(FILE_PREVIEW_STORAGE_KEY, JSON.stringify(payload))
  } catch {
    // FALLBACK(ux): quota / private mode — the preview tab hydrates empty; BroadcastChannel may still deliver
  }
}

export function postFilePreviewBroadcast(payload: FilePreviewPayload): void {
  if (typeof BroadcastChannel === 'undefined') return
  try {
    const channel = new BroadcastChannel(FILE_PREVIEW_CHANNEL)
    channel.postMessage(payload)
    channel.close()
  } catch {
    // FALLBACK(ux): channel unsupported — other tabs still receive the storage event
  }
}

export function tryOpenPreviewWindow(): boolean {
  if (typeof window === 'undefined' || isFilePreviewRoute()) return false
  const opened = window.open(filePreviewHref(), FILE_PREVIEW_WINDOW_NAME)
  return opened != null && opened.closed !== true
}

export function subscribeFilePreviewBroadcast(
  onPayload: (payload: FilePreviewPayload) => void,
): () => void {
  if (typeof window === 'undefined') return () => {}
  const onStorage = (event: StorageEvent) => {
    if (event.key !== FILE_PREVIEW_STORAGE_KEY || !event.newValue) return
    try {
      const parsed = parseFilePreviewSnapshot(JSON.parse(event.newValue) as unknown)
      if (parsed) onPayload(parsed)
    } catch {
      return
    }
  }
  window.addEventListener('storage', onStorage)
  let channel: BroadcastChannel | null = null
  if (typeof BroadcastChannel !== 'undefined') {
    channel = new BroadcastChannel(FILE_PREVIEW_CHANNEL)
    channel.onmessage = (event: MessageEvent) => {
      const parsed = parseFilePreviewSnapshot(event.data)
      if (parsed) onPayload(parsed)
    }
  }
  return () => {
    window.removeEventListener('storage', onStorage)
    channel?.close()
  }
}
