import { computed, type ComputedRef, type Ref } from 'vue'
import type { ChangeHistoryRecord } from '@/api/processForm'
import type { ChangeHistoryFormatting } from './useChangeHistoryFormatting'

export interface ChangeBatch {
  timestamp: string
  userId: string
  userName: string
  displayOperator: string
  taskInstanceId: string | null
  stageId: string | null
  displayStage: string
  /** 与展示名不同或需补充技术 ID 时的 tooltip */
  stageTooltip: string | null
  /** 关联任务展示：优先 BPMN/环节解析名，否则短 ID；完整 ID 见 tooltip */
  taskDisplayLabel: string
  concurrent: boolean
  rows: ChangeHistoryRecord[]
}

export interface ChangeHistoryBatches {
  groupedBatches: ComputedRef<ChangeBatch[]>
}

/** 同一操作：同一用户、同一任务/环节，且时间戳间隔在阈值内（一次保存内多条字段记录） */
const SAME_ACTION_MS = 80

/** 将历史记录按「同一保存动作」分组为批次；纯计算，依赖格式化解析批次表头。 */
export function useChangeHistoryBatches(
  records: Ref<ChangeHistoryRecord[]>,
  formatting: ChangeHistoryFormatting,
  dayjs: typeof import('dayjs'),
): ChangeHistoryBatches {
  function sameSaveAction(a: ChangeHistoryRecord, b: ChangeHistoryRecord): boolean {
    if (a.userId !== b.userId) return false
    if ((a.taskInstanceId ?? '') !== (b.taskInstanceId ?? '')) return false
    if ((a.stageId ?? '') !== (b.stageId ?? '')) return false
    const da = dayjs(a.timestamp)
    const db = dayjs(b.timestamp)
    if (!da.isValid() || !db.isValid()) return false
    return Math.abs(da.diff(db)) <= SAME_ACTION_MS
  }

  const groupedBatches = computed<ChangeBatch[]>(() => {
    const sorted = [...records.value].sort((a, b) => {
      const ta = dayjs(a.timestamp).valueOf()
      const tb = dayjs(b.timestamp).valueOf()
      if (ta !== tb) return ta - tb
      return (Number(a.id) || 0) - (Number(b.id) || 0)
    })

    const batches: ChangeBatch[] = []
    for (const r of sorted) {
      const last = batches[batches.length - 1]
      const header = formatting.batchHeaderFields(r)
      if (last && last.rows.length > 0 && sameSaveAction(last.rows[last.rows.length - 1]!, r)) {
        last.rows.push(r)
        if (r.concurrent) last.concurrent = true
      } else {
        batches.push({
          timestamp: r.timestamp,
          userId: r.userId,
          userName: r.userName,
          displayOperator: header.displayOperator,
          taskInstanceId: r.taskInstanceId,
          stageId: r.stageId,
          displayStage: header.displayStage,
          stageTooltip: header.stageTooltip,
          taskDisplayLabel: header.taskDisplayLabel,
          concurrent: r.concurrent,
          rows: [r],
        })
      }
    }
    return batches
  })

  return { groupedBatches }
}
