import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearActiveMiConfig,
  getActiveMiKindTableIds,
  setActiveMiConfig,
} from '../useMiConfig'
import {
  registerMiKindTableIdsFromBindings,
  resolveMiBindingKindFromConfig,
} from '../miBindingKindFromConfig'

/**
 * 分类表 id 是**模块级注册表**（39 个调用点无法逐个传参），所以它的生命周期必须和
 * MI 配置严格一致：注销配置 = 清空表 id。
 *
 * <p>回归：`registerMiKindTableIdsFromBindings` 只在「FU content 有 forms 且解析成功」的分支里
 * 调用。若下一个 FU 没有 forms 或解析抛错，注册表会留着**上一个 FU 的 collection tableId**，
 * 于是新 FU 里 tableId 恰好相同的 binding 被判成 collection / participant-child ——
 * 跨 FU 串配置。修法：`setActiveMiConfig(null)` 一并清空（两条链路在无 BPMN 时都会调它）。
 */
describe('MI 分类表 id 的生命周期', () => {
  beforeEach(() => clearActiveMiConfig())

  const collection = {
    tableId: 50331,
    bindingLinkMode: 'miParticipantRow',
  }
  const child = {
    tableId: 50333,
    bindingLinkMode: 'structuralFk',
    fieldDefinitions: [{ fieldName: 'sub_task_idq', isForeignKey: true, refTableId: 50331 }],
  }

  it('注册后能判出 participant-child', () => {
    registerMiKindTableIdsFromBindings([collection, child], 50332)
    expect(getActiveMiKindTableIds().miCollectionTableId).toBe(50331)
    expect(resolveMiBindingKindFromConfig(child, null)).toBe('participant-child')
  })

  it('setActiveMiConfig(null) 必须一并清空表 id（否则跨 FU 泄漏）', () => {
    registerMiKindTableIdsFromBindings([collection, child], 50332)
    setActiveMiConfig(null)
    expect(getActiveMiKindTableIds()).toEqual({
      miCollectionTableId: null,
      primaryTableId: null,
    })
    // 上一个 FU 的 collection 已不再影响判定
    expect(resolveMiBindingKindFromConfig(child, null)).toBeNull()
  })

  it('clearActiveMiConfig 同样清空', () => {
    registerMiKindTableIdsFromBindings([collection, child], 50332)
    clearActiveMiConfig()
    expect(getActiveMiKindTableIds().miCollectionTableId).toBeNull()
  })

  it('注册一个没有 collection 的 FU 会覆盖掉上一个 FU 的表 id', () => {
    registerMiKindTableIdsFromBindings([collection, child], 50332)
    registerMiKindTableIdsFromBindings([{ tableId: 999, bindingLinkMode: 'structuralFk' }], 998)
    expect(getActiveMiKindTableIds()).toEqual({
      miCollectionTableId: null,
      primaryTableId: 998,
    })
  })
})
