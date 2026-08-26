import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  classifyBlobPreview,
  fetchStoredFileBlob,
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
  const previewBlob = shallowRef<Blob | null>(null)
  const downloading = ref(false)

  function resetContent() {
    previewBlob.value = null
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
    previewBlob.value = fetched.blob
    kind.value = await classifyBlobPreview(state.name, fetched.blob)
  }

  async function downloadCurrent() {
    if (state.cannotDownload || downloading.value) return
    let current = previewBlob.value
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

  return { state, loading, error, kind, previewBlob, downloading, downloadCurrent, close }
}
