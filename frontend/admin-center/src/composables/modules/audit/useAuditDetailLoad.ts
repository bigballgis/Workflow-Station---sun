import { onUnmounted, ref } from 'vue'
import { getAuditLog, type AuditLog } from '@/api/audit'
import { isAbortError } from '@/api/isAbortError'

/**
 * Lazy-load one audit log for the detail dialog.
 * Aborts the in-flight GET when another row is opened or the page unmounts,
 * so a slow first response cannot overwrite a later row.
 */
export function useAuditDetailLoad(
  fetchDetail: (id: string, signal: AbortSignal) => Promise<AuditLog> = defaultFetch,
) {
  const detailDialogVisible = ref(false)
  const detailLoading = ref(false)
  const currentLog = ref<AuditLog | null>(null)
  let abort: AbortController | null = null
  let seq = 0

  async function showDetailById(id: string): Promise<void> {
    abort?.abort()
    const controller = new AbortController()
    abort = controller
    const mySeq = ++seq
    detailDialogVisible.value = true
    detailLoading.value = true
    currentLog.value = null
    try {
      const log = await fetchDetail(id, controller.signal)
      if (mySeq !== seq) {
        return
      }
      currentLog.value = log
    } catch (error: unknown) {
      if (isAbortError(error) || mySeq !== seq) {
        return
      }
      detailDialogVisible.value = false
    } finally {
      if (mySeq === seq) {
        detailLoading.value = false
      }
    }
  }

  onUnmounted(() => {
    abort?.abort()
  })

  return {
    detailDialogVisible,
    detailLoading,
    currentLog,
    showDetailById,
  }
}

function defaultFetch(id: string, signal: AbortSignal): Promise<AuditLog> {
  return getAuditLog(id, { signal })
}
