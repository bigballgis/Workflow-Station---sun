import { describe, it, expect } from 'vitest'
import {
  miChildFkConfigOfBinding,
  miLinkChildRowBelongsToParticipant,
  resolveMiChildStructuralParentFk,
} from '../miLinkChildIdentity'
import {
  findMiIsolatedParentRow,
  hostRowIsMiParticipant,
  scopeLinkChildRowsToMiHostRow,
} from '../miLinkChildRows'

/**
 * 结构外键**只**按设计器配置解析，不存在列名清单兜底。
 *
 * <p>历史实现用一张写死的名字表（sub_task_id / participant_id / parent_id …）猜"哪一列指向参与者"。
 * Multi-Instance Subtask Demo 把主外键全部改名后（people.sub_task_id → sub_task_idqc），那张表两个
 * 方向都答错：别人的行因"没有已知 FK 列"被当成无归属而放行给当前用户；自己的行也因同样原因被判成
 * 别人的而在保存时丢弃。
 *
 * <p>现在 FK 列来自 binding.fieldDefinitions 里 isForeignKey 的字段，并用 refTableId 限定为
 * "指向 MI collection 表"的那一个 —— 列叫什么都无所谓，多个 FK 也不会挑错。
 *
 * <p>现场真实配置（改名后）：people.sub_task_idqc -> subtable(50331).id_idwxwc
 */
const peopleBinding = {
  fieldDefinitions: [
    { fieldName: 'idqc', isPrimaryKey: true },
    { fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: 50331 },
    { fieldName: 'age' },
  ],
  foreignKeyField: 'idqc',
  bindingLinkMode: 'structuralFk',
}
const cfg = miChildFkConfigOfBinding(peopleBinding as any, 50331)

describe('改名后按配置解析（不猜列名）', () => {
  const mine = { idqc: 'aaaaaaaa-4f89-11d3-9a0c-0305e82c0001', sub_task_idqc: 'Test-000001', age: 70 }
  const theirs = { idqc: 'bbbbbbbb-4f89-11d3-9a0c-0305e82c0002', sub_task_idqc: 'Test-000002', age: 99 }

  it('解析出改名后的 FK 值', () => {
    expect(resolveMiChildStructuralParentFk(mine, cfg)).toBe('Test-000001')
  })
  it('别人的行不属于我', () => {
    expect(miLinkChildRowBelongsToParticipant(theirs, 'Test-000001', cfg)).toBe(false)
  })
  it('我的行属于我', () => {
    expect(miLinkChildRowBelongsToParticipant(mine, 'Test-000001', cfg)).toBe(true)
  })
  it('单行归属：我的行被认', () => {
    expect(findMiIsolatedParentRow([mine], 'Test-000001', ['idqc'], cfg)).toEqual(mine)
  })
  it('单行归属：别人的行被拒', () => {
    expect(findMiIsolatedParentRow([theirs], 'Test-000001', ['idqc'], cfg)).toBeNull()
  })
  it('旧列名（未改名的 FU）同样按各自配置工作', () => {
    const legacyCfg = miChildFkConfigOfBinding({
      fieldDefinitions: [
        { fieldName: 'id', isPrimaryKey: true },
        { fieldName: 'sub_task_id', isForeignKey: true, refTableId: 50331 },
      ],
    } as any, 50331)
    const legacyRow = { id: 'cccccccc-4f89-11d3-9a0c-0305e82c0003', sub_task_id: 'Test-000001' }
    expect(resolveMiChildStructuralParentFk(legacyRow, legacyCfg)).toBe('Test-000001')
  })
  it('无配置时不猜：返回 null 而不是碰运气命中列名', () => {
    expect(resolveMiChildStructuralParentFk(mine, null)).toBeNull()
    expect(resolveMiChildStructuralParentFk({ sub_task_id: 'Test-000001' }, null)).toBeNull()
  })
  it('指向别的表的 FK 不算参与者引用', () => {
    const twoFk = miChildFkConfigOfBinding({
      fieldDefinitions: [
        { fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: 50331 },
        { fieldName: 'room_ref', isForeignKey: true, refTableId: 999 },
      ],
    } as any, 50331)
    expect(resolveMiChildStructuralParentFk({ room_ref: 'R1' }, twoFk)).toBeNull()
    expect(resolveMiChildStructuralParentFk({ room_ref: 'R1', sub_task_idqc: 'T1' }, twoFk)).toBe('T1')
  })
})

/**
 * 现场真值回归：以下配置直接抄自 dev 库改名后的 Multi-Instance Subtask Demo
 * （dw_field_definitions / dw_form_table_bindings）：
 *   people(50333)   PK=idqc       FK=sub_task_idqc -> subtable(50331).id_idwxwc
 *   binding 50547   foreign_key_field=idk   binding_link_mode=structuralFk
 * 注意 binding 上的 foreignKeyField 是 `idqc`（本行自己的主键），指向参与者的其实是
 * `sub_task_idqc` —— 正因为如此，判归属只能读 fieldDefinitions，不能读 foreignKeyField。
 */
describe('现场改名后的真实配置', () => {
  const LIVE = miChildFkConfigOfBinding({
    fieldDefinitions: [
      { fieldName: 'idqc', isPrimaryKey: true },
      { fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: 50331 },
      { fieldName: 'age' },
    ],
    foreignKeyField: 'idqc',
    bindingLinkMode: 'structuralFk',
  } as never, 50331)
  const mine = { idqc: 'aaaaaaaa-4f89-11d3-9a0c-0305e82c0001', sub_task_idqc: 'Test-000001', age: '70' }
  const theirs = { idqc: 'bbbbbbbb-4f89-11d3-9a0c-0305e82c0002', sub_task_idqc: 'Test-000002', age: '99' }

  it('FK 列解析为改名后的 sub_task_idqc', () => {
    expect(resolveMiChildStructuralParentFk(mine, LIVE)).toBe('Test-000001')
  })
  it('别人的行不属于我（提交过滤不会卷进别人的行）', () => {
    expect(miLinkChildRowBelongsToParticipant(theirs, 'Test-000001', LIVE)).toBe(false)
  })
  it('我的行属于我（保存时不会被丢弃）', () => {
    expect(miLinkChildRowBelongsToParticipant(mine, 'Test-000001', LIVE)).toBe(true)
  })
  it('单行归属：我的行被认、别人的行被拒', () => {
    expect(findMiIsolatedParentRow([mine], 'Test-000001', ['idqc'], LIVE)).toEqual(mine)
    expect(findMiIsolatedParentRow([theirs], 'Test-000001', ['idqc'], LIVE)).toBeNull()
  })
  it('刚新增、FK 尚未种下的行仍属于当前参与者', () => {
    expect(miLinkChildRowBelongsToParticipant({ age: '71' }, 'Test-000001', LIVE)).toBe(true)
  })
})

/**
 * 宿主行（MI collection 行）的识别同样按设计器主键。
 *
 * <p>历史实现只认 id_idw / rowId / participant_id / participantId 四个名字。demo FU 把 collection
 * 主键改成 id_idwxwc 后，宿主行**认不出来**，于是嵌套子表整个跳过参与者过滤 —— 一个参与者的内联
 * 表单里同时列出别人的子行（实测 2 行而非 1 行）。
 */
describe('宿主参与者行按设计器主键识别', () => {
  const CFG = miChildFkConfigOfBinding({
    fieldDefinitions: [{ fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: 50331 }],
  } as never, 50331)
  it('改名后的参与者行能认出来', () => {
    expect(hostRowIsMiParticipant({ id_idwxwc: 'Test-000001' }, ['id_idwxwc'])).toBe(true)
  })
  it('嵌套子表按参与者过滤（改名后）', () => {
    const rows = [{ sub_task_idqc: 'Test-000001' }, { sub_task_idqc: 'Test-000002' }]
    expect(scopeLinkChildRowsToMiHostRow({ id_idwxwc: 'Test-000001' }, rows, CFG, ['id_idwxwc'])).toHaveLength(1)
  })
  it('旧命名不受影响', () => {
    expect(hostRowIsMiParticipant({ id_idw: 'Test-000001' })).toBe(true)
  })
  it('非 MI 宿主行仍不过滤', () => {
    const rows = [{ x: 1 }, { x: 2 }]
    expect(scopeLinkChildRowsToMiHostRow({ id: 5 }, rows, CFG, ['id_idwxwc'])).toHaveLength(2)
  })
})
