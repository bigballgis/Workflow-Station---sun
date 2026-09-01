import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { isUploadColumn } from '@/components/subTableAddDialogHelpers'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'
import { extractFileLinks } from '@platform-shared/list/fileNames'
import {
  joinTargetFileNames,
  persistFromUploadFileList,
  persistUploadValue,
  resolveUploadMaxFiles,
  toElUploadFileList,
} from '@platform-shared/upload/uploadFieldValue'
import { queuedUploadRequest } from '@platform-shared/upload/queuedUploadRequest'

type DialogT = (key: string, named?: Record<string, unknown>) => string
type UploadListItem = { name: string; url: string; status?: string; response?: unknown }

export function useSubTableDialogUpload(
  formData: Ref<Record<string, any>>,
  columns: () => DialogColumn[],
  t: DialogT,
) {
  const uploadFileLists = ref<Record<string, UploadListItem[]>>({})

  function maxFilesOf(col: DialogColumn): number {
    return resolveUploadMaxFiles(col.props)
  }

  function writeColumn(col: DialogColumn, stored: string | Array<{ url: string; name: string }>) {
    formData.value[col.field] = stored
    const links = extractFileLinks(stored)
    uploadFileLists.value = { ...uploadFileLists.value, [col.field]: toElUploadFileList(stored) }
    const target = col.props?.fileNameTargetField
    if (target && columns().some((c) => c.field === target)) {
      formData.value[target] = joinTargetFileNames(links)
    }
  }

  function backfillUploadNames() {
    const next: Record<string, UploadListItem[]> = {}
    for (const col of columns()) {
      if (!isUploadColumn(col, formData.value[col.field])) continue
      next[col.field] = toElUploadFileList(formData.value[col.field])
    }
    uploadFileLists.value = next
  }

  function resetUploadNames() {
    uploadFileLists.value = {}
  }

  function handleUploadSuccess(
    res: unknown,
    file: { name?: string; url?: string },
    col: DialogColumn,
    uploadFiles?: UploadListItem[],
  ) {
    const list = uploadFiles ?? [
      ...toElUploadFileList(formData.value[col.field]),
      { name: String(file.name || ''), url: String(file.url || ''), status: 'success', response: res },
    ]
    writeColumn(col, persistFromUploadFileList(list, maxFilesOf(col)))
  }

  function handleUploadRemove(col: DialogColumn, uploadFiles?: UploadListItem[]) {
    writeColumn(col, persistFromUploadFileList(uploadFiles ?? [], maxFilesOf(col)))
  }

  function handleUploadError(col: DialogColumn) {
    ElMessage.error(t('subTable.uploadFailed', { field: col.label }))
  }

  function handleUploadExceed(col: DialogColumn) {
    ElMessage.warning(t('upload.limitExceed', { limit: maxFilesOf(col) }))
  }

  function clearUpload(col: DialogColumn) {
    writeColumn(col, persistUploadValue([], maxFilesOf(col)))
  }

  return {
    uploadFileLists,
    httpRequest: queuedUploadRequest,
    maxFilesOf,
    isMultiple: (col: DialogColumn) => maxFilesOf(col) > 1,
    backfillUploadNames,
    resetUploadNames,
    handleUploadSuccess,
    handleUploadRemove,
    handleUploadError,
    handleUploadExceed,
    clearUpload,
  }
}
