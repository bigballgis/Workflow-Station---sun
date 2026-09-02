import { afterEach, describe, expect, it } from 'vitest'
import { hasMiAssignmentMarker, isAssignmentConfigured } from '@/utils/miAssignmentConfig'
import {
  allSubTableRowsHaveAssignee,
  resolveAssigneeFieldForBinding,
} from '@/utils/subTableAssignment'
import { clearActiveMiConfig, setActiveMiConfig } from '../useMiConfig'

/**
 * 现场回归（FU 50005 / Assign Task 节点 Activity_0hwtl8v）：两名参与者**都已正确分派**
 * （一个按 user、一个按 role，BPMN `assigneeMode=both`），点 Approve 仍被拦下，报
 * “Assign a user to every sub-table row (use Assign) before completing.”。
 *
 * <p>真正触发的是同一表单上的 **Attachment 子表**：`resolveAssigneeFieldForBinding` 曾无视
 * binding、一律返回 FU 级的 `assigneeField`，于是附件表也拿到分派列，
 * {@code validateSubTableAssigneesForComplete} 里的 `!af && !config` 逃生口失效，
 * 附件行（本就没有分派人）被判为「未分派」。
 *
 * <p>本测试复刻 `useTaskActions.validateSubTableAssigneesForComplete` 的判定链，
 * 锁住两条：collection 正常校验、非 collection 必须被跳过。
 */
describe('Assign Task 的 Approve 校验', () => {
  afterEach(() => clearActiveMiConfig())

  /** 现场 Sub-Task Config：assigneeMode=both。 */
  const assignmentConfig = {
    allowUser: true,
    allowRole: true,
    assigneeField: 'assignee',
    roleField: 'role_code',
    buField: 'bu_code',
  }

  /** Participants（MI collection）：sub-form 画布上有 miAssignment 区块。 */
  const participants = {
    tableId: 50331,
    tableName: 'subtable',
    bindingLinkMode: 'miParticipantRow',
    assignmentConfig,
    formFields: [{ type: 'input' }, { type: 'inputNumber' }, { type: 'miAssignment' }],
    columns: [{ field: 'assignee' }, { field: 'role_code' }],
    // 一个按 user 分派、一个按 role 分派 —— 两种都合法
    data: [
      { assignee: 'user-dev', role_code: '', bu_code: '' },
      { assignee: '', role_code: 'HMDC_Index_Role', bu_code: 'hase-hmdc' },
    ],
  }

  /** Attachment（共享附件）：同一表单，无 miAssignment 区块，行里没有分派人。 */
  const attachment = {
    tableId: 50330,
    tableName: 'attachment',
    bindingLinkMode: 'structuralFk',
    assignmentConfig: undefined,
    formFields: [{ type: 'input' }, { type: 'input' }, { type: 'upload' }],
    columns: [{ field: 'idfa' }, { field: 'main_idva' }, { field: 'file' }],
    data: [{ idfa: 'a1', main_idva: 'Meeting-000001', file: 'x.pdf' }],
  }

  /** 与 useTaskActions.validateSubTableAssigneesForComplete 同构。 */
  function validate(bindings: Array<Record<string, unknown>>): boolean {
    for (const b of bindings) {
      const hasMarker = hasMiAssignmentMarker(b.formFields as never)
      if (b.assignmentConfig && !hasMarker) continue
      const config =
        hasMarker && isAssignmentConfigured(b.assignmentConfig as never)
          ? (b.assignmentConfig as never)
          : undefined
      const af =
        (config as { assigneeField?: string } | undefined)?.assigneeField
        ?? resolveAssigneeFieldForBinding(b as never)
      if (!af && !config) continue
      if (!allSubTableRowsHaveAssignee((b.data as never) ?? [], af ?? '', config)) return false
    }
    return true
  }

  it('两名参与者分别按 user / role 分派 → Approve 放行', () => {
    setActiveMiConfig({ subTableName: 'subtable', assigneeField: 'assignee' } as never)
    expect(validate([participants, attachment])).toBe(true)
  })

  it('共享附件不得触发分派校验（本 bug 的直接成因）', () => {
    setActiveMiConfig({ subTableName: 'subtable', assigneeField: 'assignee' } as never)
    expect(validate([attachment])).toBe(true)
  })

  it('participants 真有未分派行时仍必须拦下（不能因为放行附件而放过 collection）', () => {
    setActiveMiConfig({ subTableName: 'subtable', assigneeField: 'assignee' } as never)
    const unassigned = {
      ...participants,
      data: [
        { assignee: 'user-dev', role_code: '', bu_code: '' },
        { assignee: '', role_code: '', bu_code: '' },
      ],
    }
    expect(validate([unassigned, attachment])).toBe(false)
  })
})
