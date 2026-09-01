import { reactive, type InjectionKey } from 'vue'
import {
  isFilePreviewRoute,
  postFilePreviewBroadcast,
  readStoredPreviewSnapshot,
  tryOpenPreviewWindow,
  writeStoredPreviewSnapshot,
} from './filePreviewSync'

export interface FilePreviewItem {
  url: string
  name: string
  cannotDownload?: boolean
}

export interface FilePreviewPayload extends FilePreviewItem {
  items?: FilePreviewItem[]
  index?: number
}

export interface FilePreviewPlaylistApi {
  collect: () => FilePreviewItem[]
}

export const FILE_PREVIEW_PLAYLIST_KEY: InjectionKey<FilePreviewPlaylistApi> = Symbol('filePreviewPlaylist')

const state = reactive({
  visible: false,
  url: '',
  name: '',
  cannotDownload: false,
  items: [] as FilePreviewItem[],
  index: 0,
})

function applyItem(item: FilePreviewItem) {
  state.url = item.url
  state.name = item.name || item.url
  state.cannotDownload = item.cannotDownload === true
}

function resolveItems(payload: FilePreviewPayload): FilePreviewItem[] {
  const fromList = (payload.items || []).filter((item) => !!item.url)
  if (fromList.length > 0) return fromList
  if (!payload.url) return []
  return [{ url: payload.url, name: payload.name || payload.url, cannotDownload: payload.cannotDownload }]
}

export function applyFilePreviewPayload(payload: FilePreviewPayload, show: boolean): boolean {
  const items = resolveItems(payload)
  if (items.length === 0) return false
  let index = payload.index ?? items.findIndex((item) => item.url === payload.url)
  if (index < 0 || index >= items.length) index = 0
  state.items = items
  state.index = index
  applyItem(items[index])
  state.visible = show
  return true
}

function persistCurrentPreview(): void {
  const payload: FilePreviewPayload = {
    url: state.url,
    name: state.name,
    cannotDownload: state.cannotDownload,
    items: state.items,
    index: state.index,
  }
  writeStoredPreviewSnapshot(payload)
  postFilePreviewBroadcast(payload)
}

export function hydrateFilePreviewFromStorage(): boolean {
  const snap = readStoredPreviewSnapshot()
  if (!snap) return false
  return applyFilePreviewPayload(snap, true)
}

export function openFilePreview(payload: FilePreviewPayload): void {
  if (!applyFilePreviewPayload(payload, false)) return
  persistCurrentPreview()
  if (isFilePreviewRoute()) {
    state.visible = true
    return
  }
  if (tryOpenPreviewWindow()) return
  state.visible = true
}

/** Open `current`, using `items` as prev/next playlist when it contains the file. */
export function openFilePreviewFromList(current: FilePreviewItem, items: FilePreviewItem[]): void {
  if (!current.url) return
  const list = items.filter((item) => !!item.url)
  let index = list.findIndex((item) => item.url === current.url)
  if (index < 0) {
    list.unshift(current)
    index = 0
  }
  openFilePreview({ ...current, items: list, index })
}

export function showFilePreviewAt(index: number): void {
  if (index < 0 || index >= state.items.length) return
  state.index = index
  applyItem(state.items[index])
  persistCurrentPreview()
}

export function closeFilePreview(): void {
  state.visible = false
}

export function useFilePreviewState() {
  return state
}
