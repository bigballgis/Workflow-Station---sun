import { describe, expect, it } from 'vitest'
import { assignmentDisplayKey, assignmentTagClass } from '../taskAssignmentDisplay'

describe('taskAssignmentDisplay', () => {
  it('gives claim-pool rows a distinct bu-role colour, not virtual-group', () => {
    const pool = { assignmentType: 'VIRTUAL_GROUP', claimPoolTask: true, bpmnAssigneeType: 'BU_ROLE' }
    expect(assignmentDisplayKey(pool)).toBe('buRole')
    expect(assignmentTagClass(pool)).toBe('bu-role')
  })

  it('keeps user / delegated / dept-role on different classes', () => {
    expect(assignmentTagClass({ assignmentType: 'USER' })).toBe('user')
    expect(assignmentTagClass({ assignmentType: 'DELEGATED' })).toBe('delegated')
    expect(assignmentTagClass({ assignmentType: 'DEPT_ROLE' })).toBe('dept-role')
    expect(assignmentTagClass({ assignmentType: 'VIRTUAL_GROUP' })).toBe('virtual-group')
    expect(assignmentTagClass({ assignmentType: 'CANDIDATE_USERS' })).toBe('candidate-users')
  })
})
