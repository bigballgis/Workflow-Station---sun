import { reactive } from 'vue'

export interface FilePreviewPayload {
  url: string
  name: string
  cannotDownload?: boolean
}

const state = reactive({
  visible: false,
  url: '',
  name: '',
  cannotDownload: false,
})

export function openFilePreview(payload: FilePreviewPayload): void {
  if (!payload.url) return
  state.url = payload.url
  state.name = payload.name || payload.url
  state.cannotDownload = payload.cannotDownload === true
  state.visible = true
}

export function closeFilePreview(): void {
  state.visible = false
}

export function useFilePreviewState() {
  return state
}
