import { inject, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SubTableFieldT } from './subTableFieldTypes'
import { FILE_PREVIEW_PLAYLIST_KEY, openFilePreviewFromList } from '@/composables/filePreview/useFilePreview'
import { uploadPropsBlockDownload } from '@/utils/filePreview'
import { extractFileLinks } from '@platform-shared/list/fileNames'
import { formatUploadCellText } from '@platform-shared/upload/uploadFieldValue'

/** Extract filename from URL, preferring the original filename recorded in this session */
export function getFilenameFromUrl(url: string, savedName?: string): string {
  if (savedName) return savedName
  if (!url) return 'unknown file'
  try {
    const parsed = new URL(url, window.location.origin)
    const fromQuery = parsed.searchParams.get('originalName')
      || parsed.searchParams.get('fileName')
      || parsed.searchParams.get('filename')
      || parsed.searchParams.get('name')
    if (fromQuery) return decodeURIComponent(fromQuery)
    const pathPart = parsed.pathname.split('/').pop()
    return pathPart || 'unknown file'
  } catch {
    const [pathPart] = String(url).split('?')
    const last = pathPart.split('/').pop()
    return last || 'unknown file'
  }
}

export function uploadCellLabel(value: unknown, savedName?: string): string {
  const formatted = formatUploadCellText(value)
  if (formatted.count === 1 && savedName) return savedName
  return formatted.text
}

/** Upload-cell filename display + blob download state for sub-table rows. */
export function useSubTableFileDownload(t: SubTableFieldT) {
  const playlist = inject(FILE_PREVIEW_PLAYLIST_KEY, null)
  const uploadNames = ref<Record<string, string>>({})
  const downloadingKeys = ref<Record<string, boolean>>({})

  async function downloadFile(url: string, savedName: string | undefined, rowIndex: number, field: string) {
    if (!url) return
    const key = `${rowIndex}_${field}`
    if (downloadingKeys.value[key]) return

    const filename = getFilenameFromUrl(url, savedName)
    downloadingKeys.value = { ...downloadingKeys.value, [key]: true }
    const msg = ElMessage({ message: t('common.downloading'), type: 'info', duration: 0 })

    try {
      const response = await fetch(url)
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

  function previewStoredFile(
    value: unknown,
    savedName: string | undefined,
    col: { props?: Record<string, unknown> },
  ) {
    const links = extractFileLinks(value)
    const first = links[0]
    if (!first) return
    const name = links.length === 1 && savedName ? savedName : first.name
    openFilePreviewFromList(
      {
        url: first.url,
        name,
        cannotDownload: uploadPropsBlockDownload(col.props),
      },
      playlist?.collect() ?? [],
    )
  }

  return { uploadNames, downloadingKeys, downloadFile, previewStoredFile, uploadCellLabel }
}
