import { computed, type Ref } from 'vue'
import type { ChangeHistoryRecord } from '@/api/processForm'
import type { ChangeHistoryFormatting } from './useChangeHistoryFormatting'

export interface ChangeBatch {
  key: string
  timestamp: string
  endTimestamp: string
  displayOperator: string
  displayStage: string
  stageTooltip: string | null
  eventType: string | null
  concurrent: boolean
  rows: ChangeHistoryRecord[]
}

interface SaveUnit {
  key: string
  time: string
  lastTime: string
  rows: ChangeHistoryRecord[]
}

const EDIT_TYPES = new Set(['FIELD_UPDATE', 'SUB_TABLE_ROW_ADD', 'SUB_TABLE_ROW_UPDATE', 'SUB_TABLE_ROW_DELETE'])
const CONTINUOUS_EDIT_MS = 5 * 60 * 1000

function sameScope(a: ChangeHistoryRecord, b: ChangeHistoryRecord): boolean {
  return a.processInstanceId === b.processInstanceId && a.taskInstanceId === b.taskInstanceId
    && a.stageId === b.stageId && a.userId === b.userId
}

function operationTime(row: ChangeHistoryRecord): string {
  // FALLBACK(migration): pre-batch history has only detail timestamps; retain until legacy history is retired.
  return row.batchTime ?? row.timestamp
}

/** Keep an actual save atomic even if its sub-table writes took longer than five minutes. */
function saveUnits(records: ChangeHistoryRecord[], dayjs: typeof import('dayjs')): SaveUnit[] {
  const ordered = [...records].sort((a, b) =>
    dayjs(operationTime(a)).valueOf() - dayjs(operationTime(b)).valueOf() || a.id - b.id)
  const units: SaveUnit[] = []
  const saves = new Map<string, SaveUnit>()
  for (const row of ordered) {
    const key = JSON.stringify(row.batchId
      ? [row.processInstanceId, row.batchId, row.taskInstanceId, row.stageId, row.userId, EDIT_TYPES.has(row.changeType) ? 'edit' : row.changeType]
      : [row.processInstanceId, 'legacy', row.id])
    const existing = row.batchId ? saves.get(key) : undefined
    if (existing) {
      existing.rows.push(row)
    } else {
      const unit = { key, time: operationTime(row), lastTime: operationTime(row), rows: [row] }
      units.push(unit)
      if (row.batchId) saves.set(key, unit)
    }
  }
  return units
}

function canContinue(previous: SaveUnit, next: SaveUnit, dayjs: typeof import('dayjs')): boolean {
  const a = previous.rows[0]!
  const b = next.rows[0]!
  if (!EDIT_TYPES.has(a.changeType) || !EDIT_TYPES.has(b.changeType) || !sameScope(a, b)) return false
  if (!a.taskInstanceId || !b.taskInstanceId) {
    // No task/round identity: keep actual saves atomic without inferring a shared return round.
    // FALLBACK(migration): legacy taskless rows can share only their exact original write time.
    return !a.batchId && !b.batchId && a.timestamp === b.timestamp
  }
  const gap = dayjs(next.time).diff(dayjs(previous.lastTime))
  return Number.isFinite(gap) && gap >= 0 && gap <= CONTINUOUS_EDIT_MS
}

/** Group only already-visible records; independent events interrupt consecutive editing sessions. */
export function useChangeHistoryBatches(
  records: Ref<ChangeHistoryRecord[]>,
  formatting: ChangeHistoryFormatting,
  dayjs: typeof import('dayjs'),
) {
  const groupedBatches = computed<ChangeBatch[]>(() => {
    const batches: ChangeBatch[] = []
    let previous: SaveUnit | undefined
    for (const unit of saveUnits(records.value, dayjs)) {
      const row = unit.rows[0]!
      const last = batches[batches.length - 1]
      if (last && previous && canContinue(previous, unit, dayjs)) {
        last.rows.push(...unit.rows)
        last.endTimestamp = unit.lastTime
        last.concurrent ||= unit.rows.some(item => item.concurrent)
      } else {
        const header = formatting.batchHeaderFields(row)
        batches.push({ key: unit.key, timestamp: unit.time, endTimestamp: unit.lastTime,
          displayOperator: header.displayOperator, displayStage: header.displayStage,
          stageTooltip: header.stageTooltip, eventType: EDIT_TYPES.has(row.changeType) ? null : row.changeType,
          concurrent: unit.rows.some(item => item.concurrent), rows: [...unit.rows] })
      }
      previous = unit
    }
    // Keep the existing field ordering and raw values within each displayed session.
    const positions = new Map(records.value.map((row, index) => [row, index]))
    for (const batch of batches) batch.rows.sort((a, b) => positions.get(a)! - positions.get(b)!)
    return batches
  })
  return { groupedBatches }
}
