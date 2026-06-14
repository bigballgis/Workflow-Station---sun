import { ref, watch, onMounted, type Ref } from 'vue'
import type { useI18n } from 'vue-i18n'
import { getChangeHistory, type ChangeHistoryRecord } from '@/api/processForm'

type TranslateFn = ReturnType<typeof useI18n>['t']

export interface ChangeHistoryLoaderProps {
  processInstanceId: string
  snapshotTime?: string
  taskInstanceId?: string
}

export interface ChangeHistoryLoader {
  loading: Ref<boolean>
  error: Ref<string | null>
  records: Ref<ChangeHistoryRecord[]>
  loadHistory: () => Promise<void>
}

/** 拉取变更历史并按快照时间/任务过滤；延迟到空闲时机加载，监听 props 变化重载。 */
export function useChangeHistoryLoader(
  props: ChangeHistoryLoaderProps,
  t: TranslateFn,
  dayjs: typeof import('dayjs'),
): ChangeHistoryLoader {
  const loading = ref(false)
  const error = ref<string | null>(null)
  const records = ref<ChangeHistoryRecord[]>([])

  function shouldKeepRecordInSnapshot(row: ChangeHistoryRecord): boolean {
    if (!props.snapshotTime && !props.taskInstanceId) return true
    if (props.taskInstanceId && row.taskInstanceId === props.taskInstanceId) return true
    if (!props.snapshotTime) return true

    const item = dayjs(row.timestamp)
    const cutoff = dayjs(props.snapshotTime)
    if (!item.isValid() || !cutoff.isValid()) return true
    return item.valueOf() <= cutoff.valueOf()
  }

  async function loadHistory() {
    if (!props.processInstanceId) return
    loading.value = true
    error.value = null
    try {
      const res = await getChangeHistory(props.processInstanceId) as Record<string, unknown>
      const raw = res?.data ?? res
      records.value = Array.isArray(raw) ? raw.filter(shouldKeepRecordInSnapshot) : []
    } catch (e: unknown) {
      console.error('Failed to load change history:', e)
      error.value = t('changeHistory.loadFailed')
      records.value = []
    } finally {
      loading.value = false
    }
  }

  watch(() => [props.processInstanceId, props.snapshotTime, props.taskInstanceId], () => {
    loadHistory()
  })

  onMounted(() => {
    // Defer below-the-fold history so My Request detail can paint form + sub-tables first.
    if (typeof requestIdleCallback === 'function') {
      requestIdleCallback(() => loadHistory(), { timeout: 2500 })
    } else {
      setTimeout(() => loadHistory(), 50)
    }
  })

  return { loading, error, records, loadHistory }
}
