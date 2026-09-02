import { afterEach, describe, expect, it } from 'vitest'
import {
  clearActiveMiConfig,
  getActiveMiConfig,
  getActiveMiFieldNames,
  isMiCurrentNodeField,
  isMiStatusField,
  setActiveMiConfig,
} from '../useMiConfig'
import { isSubTableRowMetaField, resolveMiDashboardFieldNames } from '../subTableBindingKinds'

/**
 * MI 列名的唯一入口。
 *
 * <p>背景：`resolveMiDashboardFieldNames(miFields)` 早就支持传配置，但实测**全代码库没有
 * 任何调用点传过** —— 113 个调用点 100% 落在 `task_status` / `task_current_node` 默认值上，
 * 于是状态列改过名的 FU 静默失效，且只在那个 FU 上复现。
 *
 * <p>这里锁定：注册配置后，深层纯函数无需改签名即可拿到正确列名；
 * **未注册时返回 `null`** —— 2026-09-02 删除了平台默认列名（那两个名字其实是设计器
 * 下拉注入的假选项，子表真实列名可能完全不同）。
 */

/** 恰好用了 task_status / task_current_node 这两个名字的 FU（现在只是普通配置值，不是默认值）。 */
const DEFAULT_SCOPE = {
  subTableName: 'subtable',
  assigneeField: 'assignee',
  rowIdVariable: 'currentItem.rowId',
  miTaskStatusField: 'task_status',
  miTaskCurrentNodeField: 'task_current_node',
  collectionVariable: null,
  elementVariable: 'currentItem',
}

/** 自定义列名的 FU —— 正是此前会静默失效的那类。 */
const CUSTOM_SCOPE = {
  ...DEFAULT_SCOPE,
  subTableName: 'atm_transaction',
  assigneeField: 'handler_id',
  miTaskStatusField: 'review_state',
  miTaskCurrentNodeField: 'review_step',
}

afterEach(() => clearActiveMiConfig())

describe('活动配置的注册与清除', () => {
  it('未注册时列名为 null —— 没有平台默认值可猜（2026-09-02 删除）', () => {
    expect(getActiveMiFieldNames()).toMatchObject({
      statusField: null,
      currentNodeField: null,
    })
  })

  it('注册后返回该 FU 配置的列名', () => {
    setActiveMiConfig(CUSTOM_SCOPE as any)
    expect(getActiveMiFieldNames()).toMatchObject({
      statusField: 'review_state',
      currentNodeField: 'review_step',
      assigneeField: 'handler_id',
      subTableName: 'atm_transaction',
    })
  })

  it('清除后不残留上一个 FU 的配置（跨 FU 串配置和写死一样糟）', () => {
    setActiveMiConfig(CUSTOM_SCOPE as any)
    clearActiveMiConfig()
    expect(getActiveMiConfig()).toBeNull()
    expect(getActiveMiFieldNames().statusField).toBeNull()
  })

  it('配置里该字段为空白时返回 null，既不返回空字符串也不兜底字面量', () => {
    setActiveMiConfig({ ...DEFAULT_SCOPE, miTaskStatusField: '  ', miTaskCurrentNodeField: null } as any)
    expect(getActiveMiFieldNames()).toMatchObject({
      statusField: null,
      currentNodeField: null,
    })
  })

  it('显式传入优先于已注册配置（既有正确传值的调用点不被干扰）', () => {
    setActiveMiConfig(CUSTOM_SCOPE as any)
    expect(getActiveMiFieldNames({ statusField: 'explicit_state' }).statusField).toBe('explicit_state')
    // 未显式给的那个仍走配置
    expect(getActiveMiFieldNames({ statusField: 'explicit_state' }).currentNodeField).toBe('review_step')
  })
})

describe('isMiStatusField / isMiCurrentNodeField', () => {
  it('按配置判定，不做名字猜测', () => {
    setActiveMiConfig(CUSTOM_SCOPE as any)
    expect(isMiStatusField('review_state')).toBe(true)
    expect(isMiCurrentNodeField('review_step')).toBe(true)
    // 配置说了状态列叫 review_state，task_status 就不再是这个 FU 的状态列
    expect(isMiStatusField('task_status')).toBe(false)
  })

  it('空值不误判', () => {
    expect(isMiStatusField('')).toBe(false)
    expect(isMiStatusField(null)).toBe(false)
    expect(isMiStatusField(undefined)).toBe(false)
  })
})

describe('深层函数无需改签名即可拿到配置', () => {
  it('resolveMiDashboardFieldNames 在无显式参数时读活动配置', () => {
    // 回归：此前无调用点传 miFields，这里恒返回 task_status，改过名的 FU 静默失效。
    setActiveMiConfig(CUSTOM_SCOPE as any)
    expect(resolveMiDashboardFieldNames()).toEqual({
      statusField: 'review_state',
      currentNodeField: 'review_step',
    })
  })

  it('resolveMiDashboardFieldNames 显式参数仍然优先', () => {
    setActiveMiConfig(CUSTOM_SCOPE as any)
    expect(resolveMiDashboardFieldNames({ statusField: 'x', currentNodeField: 'y' }))
      .toEqual({ statusField: 'x', currentNodeField: 'y' })
  })

  it('isSubTableRowMetaField 认得该 FU 自定义的状态列', () => {
    setActiveMiConfig(CUSTOM_SCOPE as any)
    // 自定义列是运行时元数据，漏判会被当成用户填的业务数据
    expect(isSubTableRowMetaField('review_state')).toBe(true)
    expect(isSubTableRowMetaField('review_step')).toBe(true)
    // 业务列不受影响
    expect(isSubTableRowMetaField('amount')).toBe(false)
  })

  it('默认列名在未注册配置时仍被认作元数据（不回归既有行为）', () => {
    expect(isSubTableRowMetaField('task_status')).toBe(true)
    expect(isSubTableRowMetaField('task_current_node')).toBe(true)
  })
})
