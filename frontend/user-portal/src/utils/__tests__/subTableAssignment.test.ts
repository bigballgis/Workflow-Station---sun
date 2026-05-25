import { describe, expect, it } from 'vitest'

import { resolveAssigneeFieldForBinding } from '../subTableAssignment'

describe('resolveAssigneeFieldForBinding', () => {
  it('recognizes plain assignee columns for generic sub-tables', () => {
    expect(resolveAssigneeFieldForBinding([{ field: 'assignee' }], 'subtable')).toBe('assignee')
  })

  it('keeps participants fallback when columns omit the assignee field', () => {
    expect(resolveAssigneeFieldForBinding([{ field: 'name' }], 'participants')).toBe('assignee_user_id')
  })
})
