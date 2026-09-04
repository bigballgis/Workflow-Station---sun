/**
 * 嵌套子表描述符必须携带 **Link Mode**（`structuralFk` / `miParticipantRow`），
 * 且不得与 `bindingMode`（`EDITABLE` / `READONLY`）混为一谈。
 *
 * <p>回归背景：`SubTableAddDialog` 曾把 `:binding-link-mode` 接成 `nested.bindingMode`，
 * 于是传给嵌套 SubTableField 的是 `EDITABLE` —— 恒不等于 `miParticipantRow`。
 * FK 播种与主键分配（`filterStructuralFkMetasForBinding` /
 * `applyMiParticipantRowSeedToInitialRow`）都只认这个字面量，所以嵌在别人弹窗里的
 * MI collection 会被静默当成普通子表：既不排除 legacy foreignKeyField，
 * 也不按参与者行播种 —— 不报错，只是行接不上父行。
 */
import { describe, it, expect } from 'vitest'
import { buildNestedSubTableDescriptors } from '../nestedSubTableDescriptors'
import { filterStructuralFkMetasForBinding } from '@/utils/subTableRowRuntime'

/** 设计器里 Link Mode 选了 "MI Participant Row" 的 binding。 */
const MI_COLLECTION_BINDING = {
  bindingId: 1127,
  tableId: 391,
  tableName: 'ATM Transaction',
  bindingType: 'SUB',
  bindingMode: 'EDITABLE',
  bindingLinkMode: 'miParticipantRow',
  foreignKeyField: 'row_id',
  tableType: 'MAIN',
  tableDescription: '',
  columns: [],
  primaryKeyFields: ['row_id'],
  data: [],
} as never

const PLACED_WIDGET = [{ key: 'tx', type: 'subTable', _bindingId: 1127 }] as never

describe('嵌套子表描述符携带 Link Mode', () => {
  it('bindingLinkMode 原样带出，且与 bindingMode 互不覆盖', () => {
    const [d] = buildNestedSubTableDescriptors(PLACED_WIDGET, [MI_COLLECTION_BINDING])

    expect(d.bindingLinkMode).toBe('miParticipantRow')
    // 两个字段各自独立 —— 接错任何一个都会被这条断言抓住
    expect(d.bindingMode).toBe('EDITABLE')
    expect(d.bindingLinkMode).not.toBe(d.bindingMode)
  })

  it('把 bindingMode 当作 Link Mode 传下去，会让 MI 判定失效', () => {
    const [d] = buildNestedSubTableDescriptors(PLACED_WIDGET, [MI_COLLECTION_BINDING])
    const fkMetas = [{ fieldName: 'row_id' }, { fieldName: 'case_row_id' }] as never

    // 正确接线：collection 的 legacy foreignKeyField 被排除（它是自己的主键，不是父引用）
    expect(
      filterStructuralFkMetasForBinding(fkMetas, {
        bindingLinkMode: d.bindingLinkMode,
        bindingForeignKeyField: d.foreignKeyField,
      }).map(m => m.fieldName),
    ).toEqual(['case_row_id'])

    // 错误接线（历史 bug）：传 EDITABLE，判定落空，legacy FK 被当成结构外键留下
    expect(
      filterStructuralFkMetasForBinding(fkMetas, {
        bindingLinkMode: d.bindingMode,
        bindingForeignKeyField: d.foreignKeyField,
      }).map(m => m.fieldName),
    ).toEqual(['row_id', 'case_row_id'])
  })

  it('没有配置 Link Mode 时为 null，不伪造默认值', () => {
    const noLinkMode = { ...(MI_COLLECTION_BINDING as Record<string, unknown>) }
    delete noLinkMode.bindingLinkMode
    const [d] = buildNestedSubTableDescriptors(PLACED_WIDGET, [noLinkMode as never])

    expect(d.bindingLinkMode).toBeNull()
  })
})
