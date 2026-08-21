import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * 子表逐操作权限（新增 / 编辑 / 删除）——设计器右侧属性面板 props.allowAdd/allowEdit/allowDelete。
 *
 * 语义（运行时与设计器预览 SubTableField.vue 的 canAdd/canEdit/canDelete 一致）：
 *   - editable 是总开关：editable === false ⇒ 三项一律 false。
 *   - 逐项标志缺省/undefined ⇒ 视为放开（历史表单三项全开，不回归）。
 *   - 逐项标志显式 false ⇒ 隐藏该操作。
 *   ⇒ effective = editable && (flag !== false)
 */

// 复刻组件中的判定，作为单一事实来源被两个 SubTableField.vue 引用的公式的行为规格
function canDo(editable: boolean | undefined, flag: boolean | undefined): boolean {
  return editable === true && flag !== false
}

describe('Sub-table per-operation permission', () => {
  const boolOrUndef = fc.constantFrom<boolean | undefined>(true, false, undefined)

  it('editable=false 时三项一律关闭，无论逐项标志', () => {
    fc.assert(
      fc.property(boolOrUndef, boolOrUndef, boolOrUndef, (add, edit, del) => {
        expect(canDo(false, add)).toBe(false)
        expect(canDo(false, edit)).toBe(false)
        expect(canDo(false, del)).toBe(false)
        expect(canDo(undefined, add)).toBe(false)
      }),
      { numRuns: 50 },
    )
  })

  it('历史数据（editable=true 且逐项标志缺省）⇒ 三项全开', () => {
    expect(canDo(true, undefined)).toBe(true)
  })

  it('editable=true 时显式 false 关闭该项，其余不受影响', () => {
    // 只禁用删除
    expect(canDo(true, true)).toBe(true) // add
    expect(canDo(true, true)).toBe(true) // edit
    expect(canDo(true, false)).toBe(false) // delete
  })

  it('effective = editable && (flag !== false)', () => {
    fc.assert(
      fc.property(fc.boolean(), boolOrUndef, (editable, flag) => {
        expect(canDo(editable, flag)).toBe(editable === true && flag !== false)
      }),
      { numRuns: 100 },
    )
  })
})

/**
 * ACTION 绑定（FORM_POPUP 弹窗写入的记录表，如 "Meeting Remark"）挂到主画布后必须恒只读——
 * 不依赖 allowAdd/allowEdit/allowDelete props 的值（设计器属性面板本就不再暴露这三个开关，
 * 见 FormDesigner.vue componentRule.subTable.rule()），运行时用 bindingType 直接短路 editable。
 */
function resolveEditableForBinding(bindingType: string | undefined, configuredEditable: boolean): boolean {
  if (bindingType === 'ACTION') return false
  return configuredEditable
}

describe('ACTION binding forces read-only regardless of allow* flags', () => {
  const boolOrUndef = fc.constantFrom<boolean | undefined>(true, false, undefined)

  it('ACTION binding ⇒ editable=false ⇒ 三项一律关闭，无论 configuredEditable/allow* 取值', () => {
    fc.assert(
      fc.property(fc.boolean(), boolOrUndef, boolOrUndef, boolOrUndef, (configuredEditable, add, edit, del) => {
        const editable = resolveEditableForBinding('ACTION', configuredEditable)
        expect(editable).toBe(false)
        expect(canDo(editable, add)).toBe(false)
        expect(canDo(editable, edit)).toBe(false)
        expect(canDo(editable, del)).toBe(false)
      }),
      { numRuns: 50 },
    )
  })

  it('SUB binding 不受影响，沿用 configuredEditable', () => {
    fc.assert(
      fc.property(fc.boolean(), (configuredEditable) => {
        expect(resolveEditableForBinding('SUB', configuredEditable)).toBe(configuredEditable)
      }),
      { numRuns: 20 },
    )
  })
})
