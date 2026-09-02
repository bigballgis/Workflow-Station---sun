import { describe, expect, it } from 'vitest'

import {
  allSubTableRowsHaveAssignee,
  resolveAssigneeFieldForBinding,
} from '../subTableAssignment'
import { setActiveMiConfig, clearActiveMiConfig } from '@/composables/tasks/useMiConfig'

describe('resolveAssigneeFieldForBinding', () => {
  it('recognizes plain assignee columns for generic sub-tables', () => {
    expect(resolveAssigneeFieldForBinding([{ field: 'assignee' }], 'subtable')).toBe('assignee')
  })

  /**
   * 表名兜底已删除：表名叫 participants 不再凭空返回 assignee_user_id
   * （表名一改即失效，且普通子表会被安上并不存在的分派列）。
   */
  it('列里没有分派列且未注册 Sub-Task Config 时返回 undefined（不按表名猜）', () => {
    expect(resolveAssigneeFieldForBinding([{ field: 'name' }], 'participants')).toBeUndefined()
  })

  it('Sub-Task Config 的 assigneeField 优先于列名推断', () => {
    setActiveMiConfig({ subTableName: 'subtable', assigneeField: 'my_owner' } as never)
    try {
      expect(resolveAssigneeFieldForBinding([{ field: 'assignee' }], 'subtable')).toBe('my_owner')
    } finally {
      clearActiveMiConfig()
    }
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
