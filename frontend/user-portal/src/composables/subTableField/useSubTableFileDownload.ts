import { inject, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SubTableFieldT } from './subTableFieldTypes'
import { FILE_PREVIEW_PLAYLIST_KEY, openFilePreviewFromList } from '@/composables/filePreview/useFilePreview'
import { uploadPropsBlockDownload } from '@/utils/filePreview'

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

/** Upload-cell filename display + blob download state for sub-table rows. */
export function useSubTableFileDownload(t: SubTableFieldT) {
  const playlist = inject(FILE_PREVIEW_PLAYLIST_KEY, null)
  // key = "{rowIndex}_{field}" -> original filename (recorded during current session upload)
  const uploadNames = ref<Record<string, string>>({})
  // Set of keys currently being downloaded
  const downloadingKeys = ref<Record<string, boolean>>({})

  /** Click filename to trigger download, using fetch+Blob to avoid new tab navigation */
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
    url: string,
    savedName: string | undefined,
    col: { props?: Record<string, unknown> },
  ) {
    if (!url) return
    openFilePreviewFromList(
      {
        url,
        name: getFilenameFromUrl(url, savedName),
        cannotDownload: uploadPropsBlockDownload(col.props),
      },
      playlist?.collect() ?? [],
    )
  }

  return { uploadNames, downloadingKeys, downloadFile, previewStoredFile }
}
