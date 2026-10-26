import { effectScope, nextTick, ref } from 'vue'
import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import type { ChangeHistoryRecord } from '@/api/processForm'
import { useChangeHistoryFormatting } from '../useChangeHistoryFormatting'
import { useChangeHistoryBatches } from '../useChangeHistoryBatches'
import { useChangeHistoryExpansion } from '../useChangeHistoryExpansion'

const formatting = useChangeHistoryFormatting(((key: string) => key) as never, dayjs)
function row(id: number, seconds: number, extra: Partial<ChangeHistoryRecord> = {}): ChangeHistoryRecord {
  return { id, processInstanceId: 'process', taskInstanceId: 'task', stageId: 'stage', stageName: 'Review',
    userId: 'alice', userName: 'Alice', timestamp: dayjs('2026-10-23T10:00:00Z').add(seconds, 'second').toISOString(),
    fieldName: `field${id}`, fieldLabel: null, oldValue: 'before', newValue: 'after', changeType: 'FIELD_UPDATE',
    subTableName: null, rowIdentifier: null, concurrent: false, ...extra }
}
function batches(rows: ChangeHistoryRecord[]) {
  return useChangeHistoryBatches(ref(rows), formatting, dayjs).groupedBatches.value
}

describe('continuous editing history', () => {
  it('groups legacy saves in the same second and chains the inclusive five-minute boundary', () => {
    const result = batches([row(1, 0), row(2, 0.5), row(3, 300.5), row(4, 600.5), row(5, 901)])
    expect(result.map(batch => batch.rows.length)).toEqual([4, 1])
    expect(result[0]!.endTimestamp).toBe(row(4, 600.5).timestamp)
    expect(result.flatMap(batch => batch.rows.map(item => item.id))).toEqual([1, 2, 3, 4, 5])
  })
  it('keeps a large actual save atomic regardless of detail write duration', () => {
    const time = row(1, 0).timestamp
    const records = Array.from({ length: 405 }, (_, index) => row(index + 1, index * 2,
      { batchId: 'one-save', batchTime: time, subTableName: index ? `table${index % 3}` : null }))
    const result = batches(records)
    expect(result).toHaveLength(1)
    expect(result[0]!.rows).toHaveLength(405)
    expect(result[0]!.endTimestamp).toBe(time)
  })
  it('does not infer one return round from two taskless save batches by the same user', () => {
    const scope = { taskInstanceId: null, stageId: 'RETURN_TO_REQUESTER' }
    const result = batches([row(1, 0, { ...scope, batchId: 'return-save-one' }),
      row(2, 120, { ...scope, batchId: 'return-save-two' })])
    expect(result.map(batch => batch.rows.map(item => item.id))).toEqual([[1], [2]])
  })
  it('keeps all taskless main/sub-table rows from the same actual save together', () => {
    const scope = { taskInstanceId: null, stageId: 'RETURN_TO_REQUESTER', batchId: 'one-save',
      batchTime: row(1, 0).timestamp }
    const result = batches([row(1, 0, scope), row(2, 600, { ...scope, subTableName: 'lines' })])
    expect(result).toHaveLength(1)
    expect(result[0]!.rows).toHaveLength(2)
  })
  it('groups legacy taskless fields only at the same exact timestamp', () => {
    const scope = { taskInstanceId: null, stageId: 'RETURN_TO_REQUESTER' }
    const result = batches([row(1, 0, scope), row(2, 0, scope), row(3, 0.01, scope)])
    expect(result.map(batch => batch.rows.map(item => item.id))).toEqual([[1, 2], [3]])
  })
  it('does not merge taskless new and legacy saves even if timestamps match', () => {
    const scope = { taskInstanceId: null, stageId: 'RETURN_TO_REQUESTER' }
    expect(batches([row(1, 0, scope), row(2, 0, { ...scope, batchId: 'new-save' })])).toHaveLength(2)
  })
  it.each(['CLAIM', 'UNCLAIM', 'FORCE_UNCLAIM', 'REASSIGN', 'RECORD_NOTE_ADD', 'RECORD_NOTE_UPDATE', 'RECORD_NOTE_DELETE', 'PROCESS_INITIATION'])(
    '%s interrupts sessions and remains a separate event', changeType => {
      const result = batches([row(1, 0), row(2, 1, { changeType }), row(3, 2)])
      expect(result.map(batch => batch.rows.length)).toEqual([1, 1, 1])
      expect(result[1]!.eventType).toBe(changeType)
    })
  it.each([
    { userId: 'bob' }, { taskInstanceId: 'rework-task' }, { stageId: 'other-stage' }, { processInstanceId: 'other-process' },
  ])('never merges across scope changes: %j', scope => {
    expect(batches([row(1, 0), row(2, 1, scope), row(3, 2)])).toHaveLength(3)
  })
  it('keeps separate note events even when they occur at the same instant', () => {
    expect(batches([row(1, 0, { changeType: 'RECORD_NOTE_ADD', stageId: null }),
      row(2, 0, { changeType: 'RECORD_NOTE_ADD', stageId: null })])).toHaveLength(2)
  })
  it('mixes new and old history without changing fields, values or the supplied detail order', () => {
    const input = [row(2, 0), row(1, 0), row(3, 2, { batchId: 'new', batchTime: row(3, 2).timestamp })]
    const result = batches(input)
    expect(result).toHaveLength(1)
    expect(result[0]!.rows).toEqual(input)
    expect(batches([])).toEqual([])
  })
  it('retains collapse state across reloads, empty loading results, and additional saves', async () => {
    const scope = effectScope()
    const records = ref([row(1, 0, { batchId: 'first', batchTime: row(1, 0).timestamp })])
    const state = scope.run(() => {
      const { groupedBatches } = useChangeHistoryBatches(records, formatting, dayjs)
      return { groupedBatches, ...useChangeHistoryExpansion(groupedBatches) }
    })!
    const key = state.groupedBatches.value[0]!.key
    state.toggleBatchTable(key)
    records.value = []
    await nextTick()
    records.value = [row(1, 0, { batchId: 'first', batchTime: row(1, 0).timestamp }), row(2, 10)]
    await nextTick()
    expect(state.groupedBatches.value[0]!.key).toBe(key)
    expect(state.isBatchTableOpen(key)).toBe(false)
    state.toggleBatchTable(key)
    expect(state.isBatchTableOpen(key)).toBe(true)
    scope.stop()
  })
})
