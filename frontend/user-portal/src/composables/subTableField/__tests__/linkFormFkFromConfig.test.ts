import { describe, it, expect } from 'vitest'
import { buildFkListForChildMatch } from '@/composables/subTableField/subTableLinkFormRowMatch'

/**
 * 子表链接表单的父引用列，只从设计器配置解析。
 *
 * <p>历史实现在 binding.foreignKeyField 之后追加 14 个猜的列名（sub_task_id / participant_id /
 * user_id / owner_id …）。Multi-Instance Subtask Demo 把主外键改名后（sub_task_id → sub_task_idqc）
 * 这些猜测**一个都命中不了**，而唯一剩下的 foreignKeyField 在 People 这类子表上是 `idk`——
 * 本行自己的主键，不是父引用。于是这张"看起来很全"的表实际上匹配不到任何真实列。
 */
describe('子表链接表单 FK 列按配置解析', () => {
  it('改名后的 FK 列能被解析出来', () => {
    const list = buildFkListForChildMatch({
      foreignKeyField: 'idqc',
      fieldDefinitions: [
        { fieldName: 'idqc', isPrimaryKey: true },
        { fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: 50331 },
      ],
    } as never)
    console.log('FK 候选 =', JSON.stringify(list))
    expect(list).toContain('sub_task_idqc')
  })
  it('不再返回一堆猜的名字', () => {
    const list = buildFkListForChildMatch({
      foreignKeyField: 'idqc',
      fieldDefinitions: [{ fieldName: 'sub_task_idqc', isForeignKey: true }],
    } as never)
    expect(list).not.toContain('participant_id')
    expect(list).not.toContain('user_id')
    expect(list).not.toContain('owner_id')
    expect(list).toEqual(['idqc', 'sub_task_idqc'])
  })
  it('旧命名的 FU 照常工作（配置里就叫 sub_task_id）', () => {
    const list = buildFkListForChildMatch({
      fieldDefinitions: [{ fieldName: 'sub_task_id', isForeignKey: true }],
    } as never)
    expect(list).toEqual(['sub_task_id'])
  })
})
