import { effectScope, nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useBatchDesignerDrafts } from '../useBatchDesignerDrafts'

function setup() {
  const formId = ref<number | null>(1)
  const bindingIds = ref([101, 202])
  const collect = vi.fn((canvas: { getRule?: () => unknown }) => {
    const rule = canvas.getRule?.()
    if (!Array.isArray(rule)) throw new Error('Synthetic invalid rule')
    return { rule, options: {} }
  })
  const reportFailure = vi.fn()
  const scope = effectScope()
  const drafts = scope.run(() => useBatchDesignerDrafts({ formId: () => formId.value,
    bindingIds: () => bindingIds.value, collect, reportFailure }))!
  return { drafts, formId, bindingIds, collect, reportFailure, stop: () => scope.stop() }
}

describe('batch canvas draft lifecycle', () => {
  it('ignores replaced callback cleanup within the same form generation', () => {
    const state = setup()
    const oldRef = state.drafts.refFor(101)
    oldRef({ getRule: () => [{ field: 'old' }] })
    const currentRef = state.drafts.refFor(101)
    const current = { getRule: () => [{ field: 'current' }] }
    currentRef(current)
    state.drafts.visited.value.add(101)
    oldRef(null)
    expect(state.drafts.refs.value[0]).toBe(current)
    expect(state.collect).not.toHaveBeenCalled()
    expect(state.drafts.failed.value).toBe(false)
    currentRef(null)
    expect(state.collect).toHaveBeenCalledOnce()
    state.stop()
  })

  it('ignores old cleanup after reopening the same form with the same binding', () => {
    const state = setup()
    const oldRef = state.drafts.refFor(101)
    oldRef({ getRule: () => [{ field: 'old' }] })
    state.formId.value = null
    state.formId.value = 1
    const current = { getRule: () => [{ field: 'current' }] }
    state.drafts.refFor(101)(current)
    oldRef(null)
    expect(state.drafts.refs.value[0]).toBe(current)
    expect(state.drafts.failed.value).toBe(false)
    state.stop()
  })

  it('keeps a healthy unmounted canvas by binding and survives binding reorder', () => {
    const state = setup()
    const canvas = { getRule: () => [{ field: 'synthetic' }] }
    state.drafts.register(101, canvas)
    state.drafts.visited.value.add(101)
    state.bindingIds.value = [202, 101]
    expect(state.drafts.refs.value[1]).toBe(canvas)
    state.drafts.register(101, null)
    expect(state.drafts.cache.value[101]?.rule).toEqual([{ field: 'synthetic' }])
    expect(state.drafts.failed.value).toBe(false)
    state.stop()
  })

  it('blocks stale cache use and visibly reports failed collection until reset', () => {
    const state = setup()
    state.drafts.cache.value[101] = { rule: [{ field: 'old' }], options: {} }
    state.drafts.visited.value.add(101)
    state.drafts.register(101, { getRule: () => { throw new Error('Synthetic read failure') } })
    state.drafts.register(101, null)
    expect(state.drafts.failed.value).toBe(true)
    expect(state.reportFailure).toHaveBeenCalledOnce()
    state.drafts.register(101, { getRule: () => [{ field: 'remounted' }] })
    expect(state.drafts.failed.value).toBe(true)
    state.drafts.reset()
    expect(state.drafts.failed.value).toBe(false)
    state.stop()
  })

  it('does not carry old form cache, visited state or late callbacks into the next form', async () => {
    const state = setup()
    state.drafts.visited.value.add(101)
    state.drafts.register(101, { getRule: () => { throw new Error('Old form failure') } })
    state.drafts.cache.value[101] = { rule: [{ field: 'old' }], options: {} }
    state.formId.value = 2
    state.bindingIds.value = [303]
    const next = { getRule: () => [{ field: 'new' }] }
    state.drafts.register(303, next)
    state.drafts.register(101, null)
    await nextTick()
    expect(state.drafts.refs.value).toEqual([next])
    expect(state.drafts.cache.value).toEqual({})
    expect(state.drafts.visited.value.size).toBe(0)
    expect(state.drafts.failed.value).toBe(false)
    expect(state.reportFailure).not.toHaveBeenCalled()
    state.stop()
  })
})
