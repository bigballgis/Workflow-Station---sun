import { describe, expect, it } from 'vitest'

import {
  allSubTableRowsHaveAssignee,
  resolveAssigneeFieldForBinding,
} from '../subTableAssignment'

describe('resolveAssigneeFieldForBinding', () => {
  it('recognizes plain assignee columns for generic sub-tables', () => {
    expect(resolveAssigneeFieldForBinding([{ field: 'assignee' }], 'subtable')).toBe('assignee')
  })

  it('keeps participants fallback when columns omit the assignee field', () => {
    expect(resolveAssigneeFieldForBinding([{ field: 'name' }], 'participants')).toBe('assignee_user_id')
  })
})

describe('allSubTableRowsHaveAssignee', () => {
  it('accepts generic configured user or role assignments', () => {
    const config = {
      allowUser: true,
      allowRole: true,
      assigneeField: 'owner_user_id',
      roleField: 'approver_role',
      buField: 'department_code',
    }
    expect(allSubTableRowsHaveAssignee([
      { owner_user_id: 'u-1' },
      { approver_role: 'REVIEWER' },
    ], 'owner_user_id', config)).toBe(true)
    expect(allSubTableRowsHaveAssignee([
      { role_code: 'legacy-role' },
    ], 'owner_user_id', config)).toBe(false)
  })
})
