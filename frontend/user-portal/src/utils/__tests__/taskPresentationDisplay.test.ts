import { describe, expect, it } from 'vitest'
import { formatTodoCurrentAssignee } from '../todoCurrentAssignee'

describe('authoritative task assignment display', () => {
  it('shows the resolved BU and role for an unheld pool', () => {
    const task = { assigneeName: 'Candidate A, Candidate B', assigneeDisplay: 'Finance + Department Manager' }
    expect(formatTodoCurrentAssignee(task, 'You')).toBe('Finance + Department Manager')
  })
  it('shows the actual holder name even when held by the viewer', () => {
    const task = { assignee: 'person-a', assigneeName: 'Person A', claimedByCurrentUser: true, assigneeDisplay: 'Person A' }
    expect(formatTodoCurrentAssignee(task, 'You')).toBe('Person A')
  })
})
