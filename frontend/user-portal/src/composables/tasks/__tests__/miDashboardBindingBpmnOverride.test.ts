import { describe, expect, it } from 'vitest'
import { isMiDashboardSubTableBinding } from '../subTableBindingKinds'
import { finalizeMiCollectionSubTableBindingRows } from '../miCollectionSubTable'

/**
 * BPMN 事实覆盖列名启发式。
 *
 * <p>`isMiDashboardSubTableBinding` 只能靠列名猜，于是从多实例会议 demo 复制出来的 FU——子表
 * 保留着 `assignee_user_id`，但 BPMN 里早已没有多实例子流程——被误判成 MI 汇总网格。后果不是
 * 显示样式问题：MI 的幽灵行过滤器要求每行带子表主键，AP 服务任务写回的行不带，于是整张表被清空。
 * 任务详情加载器解析过 BPMN，知道真相，用 `miCollection: false` 盖掉猜测。
 */
describe('isMiDashboardSubTableBinding — miCollection 覆盖', () => {
  /** 会议 demo 副本：列名像 MI，实际 BPMN 无多实例。 */
  const lookalike = {
    tableName: 'participants_copy',
    columns: [{ field: 'name' }, { field: 'email' }, { field: 'assignee_user_id' }],
  }

  it('不带 miCollection 时维持原启发式(判为 MI)——没有 BPMN 的场景行为不变', () => {
    expect(isMiDashboardSubTableBinding(lookalike)).toBe(true)
    expect(isMiDashboardSubTableBinding({ ...lookalike, miCollection: undefined })).toBe(true)
    expect(isMiDashboardSubTableBinding({ ...lookalike, miCollection: null })).toBe(true)
  })

  it('miCollection=false 时判为非 MI', () => {
    expect(isMiDashboardSubTableBinding({ ...lookalike, miCollection: false })).toBe(false)
  })

  it('miCollection=true 不放宽判定，真 MI 仍是 MI', () => {
    expect(
      isMiDashboardSubTableBinding({
        tableName: 'participants',
        columns: [{ field: 'id' }],
        miCollection: true,
      }),
    ).toBe(true)
  })

  it('覆盖后，无主键的服务任务行不再被 MI 幽灵行过滤器丢弃', () => {
    // AP 写回的行只有业务字段 + 子表 PK 之外的 id，没有 id_idw。
    const apRows = [
      { id_idw: 'csv-1', name: 'Alice Chan', email: 'alice.chan@example.com' },
      { name: 'Bob Wong', email: 'bob.wong@example.com' },
    ]

    // MI 过滤器本身不变:没有 PK 的行照旧丢弃(这是它压制幽灵行的本职)。
    expect(finalizeMiCollectionSubTableBindingRows(apRows, lookalike)).toHaveLength(1)

    // 真正的修复在于:这个绑定压根不该走 MI 过滤器。
    expect(isMiDashboardSubTableBinding({ ...lookalike, miCollection: false })).toBe(false)
  })
})
