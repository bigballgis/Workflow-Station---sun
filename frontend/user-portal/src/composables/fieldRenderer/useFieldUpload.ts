// ---------------------------------------------------------------------------
// FieldRenderer — upload URL resolution + file list (Task 6.8, Req 24)
// Behaviour copied verbatim from FieldRenderer.vue. Registers the modelValue
// watch that backfills the file list from a URL string.
// ---------------------------------------------------------------------------
import { ref, computed, watch } from 'vue'
import type { FieldRendererProps, FieldRendererEmit } from './types'

// Priority: props.uploadUrl → field.uploadUrl → default '/api/v1/upload'
const DEFAULT_UPLOAD_URL = '/api/v1/upload'

export function useFieldUpload(props: FieldRendererProps, emit: FieldRendererEmit) {
  const resolvedUploadUrl = computed(() => {
    if (props.uploadUrl) return props.uploadUrl
    if (props.field.uploadUrl && props.field.uploadUrl !== '/') return props.field.uploadUrl
    return DEFAULT_UPLOAD_URL
  })

  // Upload file list (local state for display)
  const fileList = ref<Array<{ name: string; url: string; uid?: number }>>([])

  function extractFileNameFromUrl(url: string): string {
    if (!url) return ''
    try {
      const parsed = new URL(url, window.location.origin)
      const fromQuery = parsed.searchParams.get('originalName')
        || parsed.searchParams.get('fileName')
        || parsed.searchParams.get('filename')
        || parsed.searchParams.get('name')
      if (fromQuery) return decodeURIComponent(fromQuery)
      const pathPart = parsed.pathname.split('/').pop() || url
      return decodeURIComponent(pathPart)
    } catch {
      const [pathPart] = String(url).split('?')
      return decodeURIComponent(pathPart.split('/').pop() || url)
    }
  }

  // Initialise file list from modelValue when it's a URL string
  watch(
    () => props.modelValue,
    (val) => {
      if (props.field.type === 'upload' && val && fileList.value.length === 0) {
        const url = String(val)
        const targetField = (props.field as any).fileNameTargetField
        const targetName = targetField ? props.formData?.[targetField] : undefined
        const fileName = (typeof targetName === 'string' && targetName.trim().length > 0)
          ? targetName
          : extractFileNameFromUrl(url)
        fileList.value = [{ name: fileName, url }]
      }
    },
    { immediate: true },
  )

  function onUploadSuccess(response: any, file: any) {
    const url = response?.data?.url || ''
    fileList.value = [{ name: file.name, url, uid: file.uid }]
    emit('update:modelValue', url)
    emit('upload:success', response, file, props.field.key)
  }

  function onUploadRemove(file: any) {
    fileList.value = []
    emit('update:modelValue', '')
    emit('upload:remove', file, props.field.key)
  }

  return {
    resolvedUploadUrl,
    fileList,
    onUploadSuccess,
    onUploadRemove,
  }
}
