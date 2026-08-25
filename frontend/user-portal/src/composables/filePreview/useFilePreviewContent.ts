import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  fetchStoredFileBlob,
  resolveFilePreviewKind,
  triggerBlobDownload,
  type FilePreviewKind,
} from '@/utils/filePreview'
import { closeFilePreview, useFilePreviewState } from './useFilePreview'

export function useFilePreviewContent() {
  const { t } = useI18n()
  const state = useFilePreviewState()
  const loading = ref(false)
  const error = ref('')
  const kind = ref<FilePreviewKind>('unsupported')
  const objectUrl = ref('')
  const textContent = ref('')
  const downloading = ref(false)
  let blob: Blob | null = null

  function resetContent() {
    if (objectUrl.value) {
      URL.revokeObjectURL(objectUrl.value)
      objectUrl.value = ''
    }
    blob = null
    textContent.value = ''
    error.value = ''
    kind.value = 'unsupported'
  }

  async function loadPreview() {
    resetContent()
    if (!state.visible || !state.url) return
    loading.value = true
    const fetched = await fetchStoredFileBlob(state.url)
    loading.value = false
    if (!fetched.ok) {
      error.value = fetched.result === 'not-found'
        ? t('common.fileNotFound')
        : t('filePreview.loadFailed')
      return
    }
    blob = fetched.blob
    kind.value = resolveFilePreviewKind(state.name, blob.type)
    if (kind.value === 'text') {
      textContent.value = await blob.text()
      return
    }
    if (kind.value === 'image' || kind.value === 'pdf') {
      objectUrl.value = URL.createObjectURL(blob)
    }
  }

  async function downloadCurrent() {
    if (state.cannotDownload || downloading.value) return
    let current = blob
    if (!current) {
      downloading.value = true
      const fetched = await fetchStoredFileBlob(state.url)
      downloading.value = false
      if (!fetched.ok) {
        ElMessage.error(fetched.result === 'not-found' ? t('common.fileNotFound') : t('common.downloadFailed'))
        return
      }
      current = fetched.blob
    }
    triggerBlobDownload(current, state.name)
  }

  function close() {
    closeFilePreview()
    resetContent()
  }

  watch(
    () => [state.visible, state.url] as const,
    () => {
      void loadPreview()
    },
  )

  onBeforeUnmount(() => {
    resetContent()
  })

  return { state, loading, error, kind, objectUrl, textContent, downloading, downloadCurrent, close }
}
