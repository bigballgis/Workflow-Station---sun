import { describe, expect, it } from 'vitest'
import { projectBatchAssignmentHistory } from '../batchAssignmentHistoryProjection'
import { batchAssignmentFeedback } from '../batchAssignmentFeedback'
const records = ['10:00:00', '12:00:00'].map((time, index) => ({ id: String(index), taskId: `task-${index}`,
  operationTime: `2026-10-17T${time}`, batchAssignmentVerification: { status: 'FAILED' as const, issues: [] } }))

describe('assignment history projection and feedback', () => {
  it('keeps a time-only snapshot from showing future results', () => {
    expect(projectBatchAssignmentHistory(records, { time: '2026-10-17T11:00:00' })).toEqual([records[0]])
  })
  it('keeps the exact source task observation even when recorded after its completion time', () => {
    expect(projectBatchAssignmentHistory(records, { taskId: 'task-1', time: '2026-10-17T11:00:00' })).toEqual(records)
  })
  it('limits missing or malformed cutoffs to the exact known source task', () => {
    expect(projectBatchAssignmentHistory(records, { name: 'Old task' })).toEqual([])
    expect(projectBatchAssignmentHistory(records, { time: 'bad-time', taskId: 'task-0' })).toEqual([records[0]])
  })
  it('keeps ordinary live history unchanged', () => {
    expect(projectBatchAssignmentHistory(records, {})).toBe(records)
  })
  it.each(['task', 'processStart'] as const)('keeps both errors in %s feedback', context => {
    expect(batchAssignmentFeedback({ status: 'FAILED', issues: [], historyRecorded: false }, key => key, context))
      .toBe(`${context}.batchAssignmentFailed task.batchHistory.saveFailed`)
  })
})
