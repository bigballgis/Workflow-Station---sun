import { describe, expect, it } from 'vitest'

import {
  allSubTableRowsHaveAssignee,
  resolveAssigneeFieldForBinding,
} from '../subTableAssignment'
import { setActiveMiConfig, clearActiveMiConfig } from '@/composables/tasks/useMiConfig'

/**
 * 分派列**只**来自 Sub-Task Config 的 `assigneeField`，且**只适用于 MI collection**。
 *
 * <p>列名推断（`assignee_user_id` / `assignee_id` / 正则 `/assignee/i`）与表名兜底
 * （`participants`）已一并删除 —— 与其它被删的启发式同类：改名即失效。
 */
describe('resolveAssigneeFieldForBinding', () => {
  const COLLECTION = { tableId: 50331, bindingLinkMode: 'miParticipantRow' }
  const ATTACHMENT = { tableId: 50330, bindingLinkMode: 'structuralFk' }

  it('MI collection：返回 Sub-Task Config 配置的列名', () => {
    setActiveMiConfig({ subTableName: 'subtable', assigneeField: 'my_owner' } as never)
    try {
      expect(resolveAssigneeFieldForBinding(COLLECTION))
        .toBe('my_owner')
    } finally {
      clearActiveMiConfig()
    }
  })

  /**
   * 回归（现场 FU 50005 / Assign Task 表单）：Attachment 子表与 Participants 同表单，
   * 此前 `resolveAssigneeFieldForBinding` 无视 binding 一律返回 FU 级 assigneeField，
   * 于是附件表也被当成「需要分派」，Approve 恒被拦下报
   * “Assign a user to every sub-table row”。附件行本就没有分派人。
   * 返回 undefined 同时承载「跳过分派校验」语义。
   */
  it('非 collection（共享附件）：返回 undefined，绝不套用 collection 的分派列', () => {
    setActiveMiConfig({ subTableName: 'subtable', assigneeField: 'assignee' } as never)
    try {
      expect(resolveAssigneeFieldForBinding(ATTACHMENT)).toBeUndefined()
    } finally {
      clearActiveMiConfig()
    }
  })

  /** 列名推断（assignee_user_id / assignee_id / 正则）已随硬编码名单一并删除。 */
  it('非 collection 即便列名带 assignee 也不返回分派列', () => {
    setActiveMiConfig({ subTableName: 'subtable', assigneeField: 'assignee' } as never)
    try {
      const looksLikeAssignee = {
        tableId: 50330,
        bindingLinkMode: 'structuralFk',
        columns: [{ field: 'assignee_user_id' }],
      }
      expect(resolveAssigneeFieldForBinding(looksLikeAssignee)).toBeUndefined()
    } finally {
      clearActiveMiConfig()
    }
  })

  it('未传 binding → undefined；是 collection 但未注册配置 → 同样 undefined（不猜）', () => {
    expect(resolveAssigneeFieldForBinding(undefined)).toBeUndefined()
    expect(resolveAssigneeFieldForBinding(COLLECTION)).toBeUndefined()
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
