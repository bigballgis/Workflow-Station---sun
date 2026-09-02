import { describe, expect, it } from 'vitest'
import { resolveMiSubProcessScopeFromBpmn } from '../miSubProcessScopeBpmn'
import { miLinkChildRowBelongsToParticipant } from '../miLinkChildIdentity'

/**
 * 一个 MI 子流程**只拆分一次**：只有拆分节点（`sub form1`）配 Sub-Task Config；
 * 同一子流程里后续的节点（`sub form2`、复核步骤…）是在**已经分好的子任务里**继续做，
 * 设计器**有意不配**。这些节点必须继承拆分节点的**整份契约**。
 *
 * <p>回归（FU fu-20260422 现场）：此前只继承 `subTableName`，`assigneeField` 落到 `null`，
 * 于是 `sub form2` 的参与者身份在读链路与写链路解析出**不同的值**——
 * 按 `Test-000002` 保存、按 `Test-000001` 过滤显示，用户加的 People 行存进去了却永远看不到，
 * 下一次保存又被当陈旧数据丢掉。
 */
const BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn"
                  xmlns:custom="http://custom">
  <bpmn:process id="p1">
    <bpmn:subProcess id="Activity_mi" name="multi">
      <bpmn:multiInstanceLoopCharacteristics
          flowable:collection="multiInstance_subtable_collection"
          flowable:elementVariable="currentItem"/>
      <bpmn:userTask id="Activity_split" name="sub form1">
        <bpmn:extensionElements>
          <custom:properties>
            <custom:property name="subTableName" value="subtable"/>
            <custom:property name="subTableId" value="50331"/>
            <custom:property name="assigneeField" value="assignee"/>
            <custom:property name="roleField" value="role_code"/>
            <custom:property name="buField" value="bu_code"/>
            <custom:property name="rowIdVariable" value="currentItem.rowId"/>
          </custom:properties>
        </bpmn:extensionElements>
      </bpmn:userTask>
      <bpmn:userTask id="Activity_second" name="sub form2">
        <bpmn:extensionElements>
          <custom:properties>
            <custom:property name="assigneeType" value="FIXED_BU_ROLE"/>
          </custom:properties>
        </bpmn:extensionElements>
      </bpmn:userTask>
    </bpmn:subProcess>
  </bpmn:process>
</bpmn:definitions>`

describe('MI scope — 后续节点继承拆分节点的整份配置', () => {
  it('拆分节点自身的配置照常解析', () => {
    expect(resolveMiSubProcessScopeFromBpmn(BPMN, {
      userTaskId: 'Activity_split', userTaskName: 'sub form1',
    })).toMatchObject({
      subTableName: 'subtable',
      assigneeField: 'assignee',
      rowIdVariable: 'currentItem.rowId',
    })
  })

  it('未配置的后续节点继承 subTableName **和** assigneeField / rowIdVariable', () => {
    const scope = resolveMiSubProcessScopeFromBpmn(BPMN, {
      userTaskId: 'Activity_second', userTaskName: 'sub form2',
    })
    expect(scope).toMatchObject({
      subTableName: 'subtable',
      // 修复前这里是 null —— 正是参与者身份读写不一致的起点
      assigneeField: 'assignee',
      rowIdVariable: 'currentItem.rowId',
    })
  })

  it('两个节点解析出同一份 scope —— 读写链路才不会各认一个参与者', () => {
    const a = resolveMiSubProcessScopeFromBpmn(BPMN, { userTaskId: 'Activity_split' })
    const b = resolveMiSubProcessScopeFromBpmn(BPMN, { userTaskId: 'Activity_second' })
    expect(b).toEqual(a)
  })

  it('后续节点自己配了就以自己的为准（不被拆分节点覆盖）', () => {
    const xml = BPMN.replace(
      '<custom:property name="assigneeType" value="FIXED_BU_ROLE"/>',
      '<custom:property name="assigneeType" value="FIXED_BU_ROLE"/>'
      + '<custom:property name="subTableName" value="other_table"/>'
      + '<custom:property name="assigneeField" value="handler_id"/>',
    )
    expect(resolveMiSubProcessScopeFromBpmn(xml, { userTaskId: 'Activity_second' })).toMatchObject({
      subTableName: 'other_table',
      assigneeField: 'handler_id',
    })
  })

  it('整个子流程都没有 subTableName 时返回 null（不猜）', () => {
    const xml = BPMN.replace(/<custom:property name="subTableName"[^>]*\/>/g, '')
    expect(resolveMiSubProcessScopeFromBpmn(xml, { userTaskId: 'Activity_second' })).toBeNull()
  })
})

/**
 * 新增但尚未保存的 link-child 行归属判定。
 *
 * <p>提交 payload 是按这个判定过滤的（`useTaskDetailSubTableSync`），所以判成 false 的后果
 * 不是"显示不出来"而是**存不进去** —— 新行在发请求前就被剔除，后端连见都没见过。
 * 实测：用户给 People 加两行、Save 无报错、刷新后全没了。
 */
describe('miLinkChildRowBelongsToParticipant — 新增行', () => {
  it('刚新增、只有分配好的 UUID 主键 → 属于当前参与者', () => {
    expect(miLinkChildRowBelongsToParticipant(
      { id: '9d4e0000-0000-4000-8000-000000000001', age: '55' }, 'Test-000001')).toBe(true)
  })

  it('连主键都还没有的空行 → 同样属于当前参与者', () => {
    expect(miLinkChildRowBelongsToParticipant({ age: '55' }, 'Test-000001')).toBe(true)
  })

  it('已保存、结构 FK 指向我 → 属于我', () => {
    expect(miLinkChildRowBelongsToParticipant(
      { id: 'u1', sub_task_id: 'Test-000001' }, 'Test-000001')).toBe(true)
  })

  it('结构 FK 指向别人 → 不属于我（放行会把别人的行卷进我的提交）', () => {
    expect(miLinkChildRowBelongsToParticipant(
      { id: 'u1', sub_task_id: 'Test-000002' }, 'Test-000001')).toBe(false)
  })

  it('id_idw 指向别的参与者 → 不属于我', () => {
    expect(miLinkChildRowBelongsToParticipant({ id_idw: 'Test-000002' }, 'Test-000001')).toBe(false)
  })
})
