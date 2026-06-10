import { describe, expect, it } from 'vitest'

import { resolveRollbackTargetActivityId } from '../taskReturnTarget'

describe('resolveRollbackTargetActivityId', () => {
  const activities = [
    { taskId: 'Approve1', taskName: 'Approve1' },
    { taskId: 'Submit', taskName: 'Submit' },
  ]

  it('first step resolves to earliest user task in returnable list', () => {
    const target = resolveRollbackTargetActivityId('first', {}, activities)
    expect(target).toEqual({ activityId: 'Submit', taskName: 'Submit' })
  })

  it('initiator alias matches first step', () => {
    const target = resolveRollbackTargetActivityId('initiator', {}, activities)
    expect(target?.activityId).toBe('Submit')
  })

  it('previous step resolves to most recent user task', () => {
    const target = resolveRollbackTargetActivityId('previous', {}, activities)
    expect(target).toEqual({ activityId: 'Approve1', taskName: 'Approve1' })
  })
})
