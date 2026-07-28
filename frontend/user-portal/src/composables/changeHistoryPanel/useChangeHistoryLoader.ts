import { ref, watch, onMounted, type Ref } from 'vue'
import type { useI18n } from 'vue-i18n'
import { getChangeHistory, type ChangeHistoryRecord } from '@/api/processForm'

type TranslateFn = ReturnType<typeof useI18n>['t']

export interface ChangeHistoryLoaderProps {
  rowIdentifier?: string
  taskId?: string
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
/** Hides audit rows that have no user-visible value before or after the operation. */
export function excludeEmptyValueChanges(rows: ChangeHistoryRecord[]): ChangeHistoryRecord[] {
  return rows.filter(row => hasDisplayValue(row.oldValue) || hasDisplayValue(row.newValue))
}
function hasDisplayValue(value: string | null | undefined): boolean {
  return value !== null && value !== undefined && value.trim().length > 0
}
/** 拉取变更历史并按快照时间/任务过滤；挂载时立即加载，监听 props 变化重载。 */
export function useChangeHistoryLoader(
  props: ChangeHistoryLoaderProps,
  t: TranslateFn,
  dayjs: typeof import('dayjs'),
): ChangeHistoryLoader {
  const loading = ref(Boolean(props.processInstanceId))
  const error = ref<string | null>(null)
  const records = ref<ChangeHistoryRecord[]>([])

  function shouldKeepRecordInSnapshot(row: ChangeHistoryRecord): boolean {
    // When viewing from a multi-instance sub-task, only show records for this specific row
    // (plus top-level field changes that have no row identifier)
    if (props.rowIdentifier) {
      if (row.rowIdentifier != null && row.rowIdentifier !== props.rowIdentifier) return false
    }

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
      console.warn('[changeHistory] loadHistory: pid=', props.processInstanceId, 'rowId=', props.rowIdentifier, 'taskId=', props.taskId)
      const res = await getChangeHistory(props.processInstanceId, props.rowIdentifier, props.taskId) as Record<string, unknown>
      const raw = res?.data ?? res
      const filtered = Array.isArray(raw) ? raw.filter(shouldKeepRecordInSnapshot) : []
      const normalized = excludeEmptyValueChanges(filtered)
      records.value = normalized.sort((a, b) => {
        const ta = dayjs(a.timestamp)
        const tb = dayjs(b.timestamp)
        if (ta.isValid() && tb.isValid() && ta.valueOf() !== tb.valueOf()) {
          return ta.valueOf() - tb.valueOf()
        }
        const sa = a.stageId ?? ''
        const sb = b.stageId ?? ''
        if (sa !== sb) return sa.localeCompare(sb)
        const oa = a.fieldOrder ?? Number.MAX_SAFE_INTEGER
        const ob = b.fieldOrder ?? Number.MAX_SAFE_INTEGER
        if (oa !== ob) return oa - ob
        const fa = a.fieldLabel || a.fieldName || ''
        const fb = b.fieldLabel || b.fieldName || ''
        if (fa !== fb) return fa.localeCompare(fb)
        return (a.id ?? 0) - (b.id ?? 0)
      })
    } catch (e: unknown) {
      console.error('Failed to load change history:', e)
      error.value = t('changeHistory.loadFailed')
      records.value = []
    } finally {
      loading.value = false
    }
  }

  watch(() => [props.processInstanceId, props.snapshotTime, props.taskInstanceId, props.rowIdentifier, props.taskId], () => {
    loadHistory()
  })
  onMounted(() => { void loadHistory() })
  return { loading, error, records, loadHistory }
}
