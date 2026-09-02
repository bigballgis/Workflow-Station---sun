import { ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import {
  getFilenameFromUrl,
  isUploadColumn,
  resolveFileFetchUrl,
  resolveUploadCellUrl,
} from '@/components/designer/uploadFieldUtils'
import { formatUploadCellText } from '@platform-shared/upload/uploadFieldValue'
import type { ColumnConfig } from './types'

interface UseSubTableUploadCellsOptions {
  displayColumns: ComputedRef<ColumnConfig[]>
  uploadNames: Ref<Record<string, string>>
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * 子表单元格的富文本净化与上传文件展示/下载：
 * sanitizeHtml、上传 URL 解析、行级文件名缓存回填，以及文件下载。
 */
export function useSubTableUploadCells(options: UseSubTableUploadCellsOptions) {
  const { displayColumns, uploadNames, t } = options

  const downloadingKeys = ref<Record<string, boolean>>({})

  function sanitizeHtml(html: string): string {
    if (!html) return ''
    return DOMPurify.sanitize(html, {
      ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 's', 'ol', 'ul', 'li',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a', 'img', 'table', 'tr', 'td', 'th', 'span', 'div'],
      ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'style', 'target', 'rel'],
    })
  }

  function resolveRowUploadUrl(row: Record<string, unknown>, col: ColumnConfig): string | null {
    return resolveUploadCellUrl(row[col.field])
  }

  function rememberUploadNamesForRow(rowIndex: number, rowData: Record<string, any>) {
    for (const col of displayColumns.value) {
      if (!isUploadColumn(col, rowData[col.field])) continue
      const url = resolveUploadCellUrl(rowData[col.field])
      if (!url) continue
      const target = col.props?.fileNameTargetField as string | undefined
      const saved = (target && rowData[target] != null ? String(rowData[target]) : undefined)
        || getFilenameFromUrl(String(url))
      uploadNames.value = { ...uploadNames.value, [`${rowIndex}_${col.field}`]: saved }
    }
  }

  async function downloadFile(
    url: string,
    savedName: string | undefined,
    rowIndex: number,
    field: string,
  ) {
    if (!url) return
    const key = `${rowIndex}_${field}`
    if (downloadingKeys.value[key]) return

    const filename = getFilenameFromUrl(url, savedName)
    const fetchUrl = resolveFileFetchUrl(url)
    downloadingKeys.value = { ...downloadingKeys.value, [key]: true }
    const msg = ElMessage({ message: t('common.downloading'), type: 'info', duration: 0 })

    try {
      const response = await fetch(fetchUrl, { credentials: 'include' })
      if (!response.ok) {
        msg.close()
        ElMessage.error(response.status === 404 ? t('common.fileNotFound') : t('common.downloadFailed'))
        return
      }
      const blob = await response.blob()
      const blobUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = blobUrl
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(blobUrl)
      msg.close()
    } catch {
      msg.close()
      ElMessage.error(t('common.downloadFailed'))
    } finally {
      const next = { ...downloadingKeys.value }
      delete next[key]
      downloadingKeys.value = next
    }
  }

  function uploadCellLabel(value: unknown, savedName?: string): string {
    const formatted = formatUploadCellText(value)
    if (formatted.count === 1 && savedName) return savedName
    return formatted.text
  }

  return {
    downloadingKeys,
    sanitizeHtml,
    resolveRowUploadUrl,
    rememberUploadNamesForRow,
    downloadFile,
    uploadCellLabel,
  }
}
