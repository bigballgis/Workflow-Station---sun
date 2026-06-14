import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { isUploadColumn } from '@/components/subTableAddDialogHelpers'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'

/** i18n translate signature (kept loose to match the SFC's useI18n usage). */
type DialogT = (key: string, named?: Record<string, unknown>) => string

/**
 * Upload column state for the sub-table add/edit dialog: file-name tags,
 * success/error/clear handlers and URL → filename back-fill.
 *
 * Behaviour is preserved verbatim from the original SFC — including
 * auto-filling the configured fileNameTargetField on success.
 */
export function useSubTableDialogUpload(
  formData: Ref<Record<string, any>>,
  columns: () => DialogColumn[],
  t: DialogT,
) {
  const uploadNames = ref<Record<string, string>>({})

  function extractFilenameFromUrl(url: string): string {
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

  /** Back-fill upload file names from URL when entering edit mode. */
  function backfillUploadNames() {
    for (const col of columns()) {
      if (isUploadColumn(col, formData.value[col.field]) && formData.value[col.field]) {
        const url: string = formData.value[col.field]
        uploadNames.value[col.field] = extractFilenameFromUrl(url)
      }
    }
  }

  function resetUploadNames() {
    uploadNames.value = {}
  }

  function handleUploadSuccess(res: any, file: any, col: DialogColumn) {
    const url: string = res?.data?.url || ''
    formData.value[col.field] = url
    uploadNames.value = { ...uploadNames.value, [col.field]: file.name }
    // Auto-fill filename to the configured target column (if any)
    const target = col.props?.fileNameTargetField
    if (target && columns().some(c => c.field === target)) {
      formData.value[target] = file.name
    }
  }

  function handleUploadError(col: DialogColumn) {
    ElMessage.error(t('subTable.uploadFailed', { field: col.label }))
  }

  function clearUpload(col: DialogColumn) {
    formData.value[col.field] = ''
    const next = { ...uploadNames.value }
    delete next[col.field]
    uploadNames.value = next
  }

  return {
    uploadNames,
    extractFilenameFromUrl,
    backfillUploadNames,
    resetUploadNames,
    handleUploadSuccess,
    handleUploadError,
    clearUpload,
  }
}
