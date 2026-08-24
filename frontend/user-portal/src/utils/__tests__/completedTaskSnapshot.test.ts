import { describe, expect, it } from 'vitest'
import {
  emptyProcessFormRef,
  extractCompletedFormFromVariables,
  hasSnapshotFieldValues,
} from '../completedTaskSnapshot'

const processFormRef = emptyProcessFormRef('pi-1')

describe('extractCompletedFormFromVariables', () => {
  it('returns null when the snapshot key is missing', () => {
    expect(extractCompletedFormFromVariables({ case_stage: { stage_name: 'Open' } }, 'task-1', processFormRef))
      .toBeNull()
  })

  it('rebuilds snapshot and live values from process variables', () => {
    const recovered = extractCompletedFormFromVariables({
      case_stage: { stage_name: 'Investigation' },
      '_snapshot_task-1': {
        taskId: 'task-1',
        taskDefinitionKey: 'review',
        assignee: 'u1',
        completedAt: '2026-08-24T00:00:00Z',
        fieldValues: {
          case_stage: { stage_name: 'Case Submission', name: 'should-not-win' },
        },
      },
    }, 'task-1', processFormRef)
    expect(recovered?.snapshot.fieldValues.case_stage).toEqual({
      stage_name: 'Case Submission',
      name: 'should-not-win',
    })
    expect(recovered?.liveValues.case_stage).toEqual({ stage_name: 'Investigation' })
  })

  it('treats empty fieldValues as having no snapshot fields', () => {
    expect(hasSnapshotFieldValues({})).toBe(false)
    expect(hasSnapshotFieldValues({ __subTables__: {} })).toBe(false)
    expect(hasSnapshotFieldValues({ case_stage: { stage_name: 'Open' } })).toBe(true)
  })
})
