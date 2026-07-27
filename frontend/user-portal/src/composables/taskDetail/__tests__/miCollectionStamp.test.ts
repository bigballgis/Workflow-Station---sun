import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { stampMiCollectionFromBpmn } from '../miCollectionStamp'
import type { TaskDetailCtx } from '../context'

/**
 * 盖章器的三条不变量。
 *
 * <p>这个 helper 被三处构建绑定的地方共用（FU 加载器、历史表单、节点表单图）。它只会盖 `false`，
 * 且只在 BPMN 真的解析过时才盖——见 {@link stampMiCollectionFromBpmn} 的注释。测试锁住这三条，
 * 因为一旦哪天有人"顺手"补上 `true` 分支，列名启发式当前拒绝的绑定会被反向误判成 MI 汇总网格。
 */
describe('stampMiCollectionFromBpmn', () => {
  function ctxOf(bpmnXml: string | null, miScope: unknown = null): TaskDetailCtx {
    return {
      bpmn: { bpmnXml: ref(bpmnXml) },
      miSubProcessScope: ref(miScope),
    } as unknown as TaskDetailCtx
  }

  it('BPMN 已解析且无多实例子流程 → 盖 false', () => {
    const bindings = [{ miCollection: undefined as boolean | null | undefined }, {}]
    stampMiCollectionFromBpmn(ctxOf('<bpmn:definitions/>'), bindings)
    expect(bindings.every(b => (b as { miCollection?: unknown }).miCollection === false)).toBe(true)
  })

  it('没有 BPMN → 不盖章，保持"未知"让列名启发式继续决定', () => {
    const bindings = [{}]
    stampMiCollectionFromBpmn(ctxOf(null), bindings)
    expect((bindings[0] as { miCollection?: unknown }).miCollection).toBeUndefined()
  })

  it('BPMN 里确实有多实例子流程 → 不盖章，仍由启发式挑出哪个绑定是汇总表', () => {
    const bindings = [{}]
    stampMiCollectionFromBpmn(ctxOf('<bpmn:definitions/>', { collectionVar: 'participants' }), bindings)
    expect((bindings[0] as { miCollection?: unknown }).miCollection).toBeUndefined()
  })

  it('绑定为空/缺失时安全返回', () => {
    expect(() => stampMiCollectionFromBpmn(ctxOf('<bpmn:definitions/>'), [])).not.toThrow()
    expect(() => stampMiCollectionFromBpmn(ctxOf('<bpmn:definitions/>'), undefined)).not.toThrow()
    expect(() => stampMiCollectionFromBpmn(ctxOf('<bpmn:definitions/>'), null)).not.toThrow()
  })
})
