import { describe, expect, it } from 'vitest'
import { isAssignmentWriteBlocked } from '../assignmentWriteAccess'
import type { AssignmentClaimAccess } from '@/types/assignmentClaimAccess'

describe('task write UI restriction', () => {
  it('keeps legacy UI behavior and does not use claimable to decide processing rights', () => {
    expect(isAssignmentWriteBlocked({})).toBe(false)
    expect(isAssignmentWriteBlocked({ assignmentClaimAccess: claim('LEGACY', false) })).toBe(false)
    expect(isAssignmentWriteBlocked({ assignmentWriteAccess: { canWrite: true, evaluation: null } })).toBe(false)
  })
  it.each(['CURRENT', 'RECONCILING', 'SYNC_PENDING', 'DEPENDENCY_UNAVAILABLE'] as const)(
    'known protection without its write snapshot is read-only: %s', (state) => {
      expect(isAssignmentWriteBlocked({ assignmentClaimAccess: claim(state, true) })).toBe(true)
    },
  )
  it('a write denial wins independently of claim information', () => {
    expect(isAssignmentWriteBlocked({ assignmentWriteAccess: { canWrite: false, evaluation: null } })).toBe(true)
  })
  it('a pending check or retained draft pauses editing even after server eligibility returns', () => {
    expect(isAssignmentWriteBlocked({ assignmentWriteCheckPending: true,
      assignmentWriteAccess: { canWrite: true, evaluation: null } })).toBe(true)
    expect(isAssignmentWriteBlocked({ assignmentLocalDraftRetained: true,
      assignmentWriteAccess: { canWrite: true, evaluation: null } })).toBe(true)
  })
})

function claim(state: AssignmentClaimAccess['state'], tracked: boolean): AssignmentClaimAccess {
  return { taskId: 'task', tracked, state, claim: { exclusionAllowsClaim: false, poolSource: null, holderSource: null }, stamp: null }
}
