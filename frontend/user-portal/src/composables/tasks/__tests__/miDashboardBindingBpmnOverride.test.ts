import { describe, expect, it } from 'vitest'
import { isMiDashboardSubTableBinding } from '../subTableBindingKinds'
import { finalizeMiCollectionSubTableBindingRows } from '../miCollectionSubTable'

/**
 * MI collection 判定只认配置 —— 「像 MI 的表」不再被误判。
 *
 * <p><b>历史</b>：`isMiDashboardSubTableBinding` 曾靠列名/表名猜（`assignee_user_id`、
 * `task_status`、表名含 `participants`）。从多实例会议 demo 复制出来的 FU——子表保留着
 * `assignee_user_id`，但 BPMN 里早已没有多实例子流程——被误判成 MI 汇总网格。后果不是显示样式
 * 问题：MI 的幽灵行过滤器要求每行带子表主键，AP 服务任务写回的行不带，于是整张表被清空。
 * 当时的对策是让任务详情加载器解析 BPMN 后用 `miCollection: false` **盖掉**猜测。
 *
 * <p><b>现在</b>：列名/表名启发式已删除，判定唯一来源是设计器 Link Mode
 * （`bindingLinkMode === 'miParticipantRow'`）。"像 MI 的表"天然就判为非 MI，
 * `miCollection: false` 这层补丁不再是唯一防线（仍保留作为 BPMN 事实的显式否决）。
 */
describe('isMiDashboardSubTableBinding — 只认配置', () => {
  /** 会议 demo 副本：列名像 MI，实际 BPMN 无多实例，且设计器没有声明 Link Mode。 */
  const lookalike = {
    tableName: 'participants_copy',
    columns: [{ field: 'name' }, { field: 'email' }, { field: 'assignee_user_id' }],
    // 设计器主键。幽灵行过滤按它判定；缺失时过滤器不再拿 id_idw 顶上（不猜列名）。
    primaryKeyFields: ['id_idw'],
  }

  it('列名像 MI 但没有 Link Mode 声明 => 非 MI（根治，不再需要 miCollection 兜底）', () => {
    expect(isMiDashboardSubTableBinding(lookalike)).toBe(false)
    expect(isMiDashboardSubTableBinding({ ...lookalike, miCollection: undefined })).toBe(false)
    expect(isMiDashboardSubTableBinding({ ...lookalike, miCollection: null })).toBe(false)
  })

  it('miCollection=false 仍然是显式否决（BPMN 无多实例时的事实）', () => {
    expect(isMiDashboardSubTableBinding({ ...lookalike, miCollection: false })).toBe(false)
  })

  it('设计器声明 Link Mode = MI Participant Row => 判为 MI', () => {
    expect(
      isMiDashboardSubTableBinding({
        tableName: 'anything',
        bindingLinkMode: 'miParticipantRow',
        columns: [{ field: 'id' }],
      }),
    ).toBe(true)
  })

  it('miCollection=false 优先于 Link Mode 声明（BPMN 事实最终否决）', () => {
    expect(
      isMiDashboardSubTableBinding({
        tableName: 'subtable',
        bindingLinkMode: 'miParticipantRow',
        miCollection: false,
      }),
    ).toBe(false)
  })

  it('原始故障场景：AP 写回的无主键行不再被 MI 幽灵行过滤器丢弃', () => {
    // AP 写回的行只有业务字段 + 子表 PK 之外的 id，没有 id_idw。
    const apRows = [
      { id_idw: 'csv-1', name: 'Alice Chan', email: 'alice.chan@example.com' },
      { name: 'Bob Wong', email: 'bob.wong@example.com' },
    ]

    // MI 过滤器本身不变：没有 PK 的行照旧丢弃（这是它压制幽灵行的本职）。
    expect(finalizeMiCollectionSubTableBindingRows(apRows, lookalike)).toHaveLength(1)

    // 真正的修复：这个绑定压根不该走 MI 过滤器 —— 现在无需任何盖章即成立。
    expect(isMiDashboardSubTableBinding(lookalike)).toBe(false)
  })
})
