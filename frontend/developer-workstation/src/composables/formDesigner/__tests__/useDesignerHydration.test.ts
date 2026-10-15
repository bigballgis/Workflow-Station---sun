import { describe, expect, it } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { useDesignerHydration } from '../useDesignerHydration'

describe('designer configuration readiness', () => {
  it('waits for mounting and cancels obsolete or disposed canvas watchers', async () => {
    const scope = effectScope()
    const state = scope.run(() => useDesignerHydration())!
    const canvas = ref<object | null>(null)
    let loads = 0
    const token = state.begin()
    state.whenMounted(token, () => canvas.value, () => { loads++ })
    canvas.value = {}
    await nextTick()
    expect(loads).toBe(1)
    canvas.value = null
    state.whenMounted(token, () => canvas.value, () => { loads++ })
    const replacement = state.begin()
    canvas.value = {}
    await nextTick()
    expect(loads).toBe(1)
    canvas.value = null
    state.whenMounted(replacement, () => canvas.value, () => { loads++ })
    scope.stop()
    canvas.value = {}
    await nextTick()
    expect(loads).toBe(1)
  })
  it('waits for metadata and every scheduled canvas, including late sub-canvases', () => {
    const state = useDesignerHydration()
    const token = state.begin()
    const main = state.track(token)
    const scheduling = state.track(token)
    state.finish(token)
    main()
    expect(state.blocked.value).toBe(true)
    const child = state.track(token)
    scheduling()
    expect(state.blocked.value).toBe(true)
    child()
    expect(state.blocked.value).toBe(false)
    child()
    expect(state.blocked.value).toBe(false)
  })

  it('old selection callbacks cannot unlock or fail the new selection', () => {
    const state = useDesignerHydration()
    const old = state.begin()
    const callback = state.track(old)
    const current = state.begin()
    callback()
    state.fail(old)
    expect(state.blocked.value).toBe(true)
    state.finish(current)
    expect(state.blocked.value).toBe(false)
  })

  it('a failed load stays unsaveable until a new successful selection', () => {
    const state = useDesignerHydration()
    const token = state.begin()
    state.fail(token)
    state.finish(token)
    expect(state.blocked.value).toBe(true)
    state.finish(state.begin())
    expect(state.blocked.value).toBe(false)
  })
})
