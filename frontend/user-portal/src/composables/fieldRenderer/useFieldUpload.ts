// ---------------------------------------------------------------------------
// FieldRenderer — upload URL resolution + file list (Task 6.8, Req 24)
// ---------------------------------------------------------------------------
import { computed, inject, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { FieldRendererProps, FieldRendererEmit } from './types'
import { FILE_PREVIEW_PLAYLIST_KEY, openFilePreviewFromList } from '@/composables/filePreview/useFilePreview'
import { isCannotDownload } from '@/utils/filePreview'
import { extractFileLinks } from '@platform-shared/list/fileNames'
import {
  DEFAULT_UPLOAD_MAX_FILES,
  joinTargetFileNames,
  persistFromUploadFileList,
  toElUploadFileList,
  uploadValueFingerprint,
} from '@platform-shared/upload/uploadFieldValue'
import { queuedUploadRequest } from '@platform-shared/upload/queuedUploadRequest'

const DEFAULT_UPLOAD_URL = '/api/v1/upload'

export function useFieldUpload(props: FieldRendererProps, emit: FieldRendererEmit) {
  const { t } = useI18n()
  const playlist = inject(FILE_PREVIEW_PLAYLIST_KEY, null)
  const resolvedUploadUrl = computed(() => {
    if (props.uploadUrl) return props.uploadUrl
    if (props.field.uploadUrl && props.field.uploadUrl !== '/') return props.field.uploadUrl
    return DEFAULT_UPLOAD_URL
  })

  const uploadLimit = computed(() => props.field.uploadLimit ?? DEFAULT_UPLOAD_MAX_FILES)
  const uploadMultiple = computed(() => uploadLimit.value > 1)
  const fileList = ref<Array<{ name: string; url: string; status?: string }>>([])

  watch(
    () => props.modelValue,
    (val) => {
      if (props.field.type !== 'upload') return
      const next = toElUploadFileList(val)
      if (uploadValueFingerprint(fileList.value) === uploadValueFingerprint(next)) return
      fileList.value = next
    },
    { immediate: true },
  )

  function persistFromList(list: Array<{ url?: string; name?: string; status?: string; response?: unknown }>) {
    const stored = persistFromUploadFileList(list, uploadLimit.value)
    fileList.value = toElUploadFileList(stored)
    emit('update:modelValue', stored)
    const target = props.field.fileNameTargetField
    if (target && props.formData) {
      props.formData[target] = joinTargetFileNames(extractFileLinks(stored))
    }
  }

  function onUploadSuccess(
    response: unknown,
    file: { name?: string; url?: string; uid?: number },
    uploadFiles?: Array<{ url?: string; name?: string; status?: string; response?: unknown }>,
  ) {
    persistFromList(uploadFiles ?? [...fileList.value, {
      url: String(file.url || ''),
      name: String(file.name || ''),
      status: 'success',
      response,
    }])
    emit('upload:success', response, file, props.field.key)
  }

  function onUploadRemove(
    file: unknown,
    uploadFiles?: Array<{ url?: string; name?: string; status?: string; response?: unknown }>,
  ) {
    persistFromList(uploadFiles ?? [])
    emit('upload:remove', file, props.field.key)
  }

  function onUploadExceed() {
    ElMessage.warning(t('upload.limitExceed', { limit: uploadLimit.value }))
  }

  function previewCurrentFile(file?: { name?: string; url?: string }) {
    const links = extractFileLinks(props.modelValue)
    const url = file?.url || links[0]?.url || ''
    if (!url) return
    const name = file?.name || links.find((l) => l.url === url)?.name || links[0]?.name || url
    openFilePreviewFromList(
      { url, name, cannotDownload: isCannotDownload(props.field.cannotDownload) },
      playlist?.collect() ?? [],
    )
  }

  return {
    resolvedUploadUrl,
    uploadLimit,
    uploadMultiple,
    fileList,
    httpRequest: queuedUploadRequest,
    onUploadSuccess,
    onUploadRemove,
    onUploadExceed,
    previewCurrentFile,
  }
}
