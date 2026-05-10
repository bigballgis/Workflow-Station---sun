import { describe, it, expect, vi } from 'vitest'
import { useToggleSwitch } from '@/composables/useToggleSwitch'

describe('useToggleSwitch', () => {
  it('initializes state with given value', () => {
    const { initState, getState } = useToggleSwitch(async () => {})
    initState('a', true)
    expect(getState('a')).toEqual({ value: true, loading: false })
  })

  it('toggle success returns { ok: true }', async () => {
    const toggleFn = vi.fn().mockResolvedValue(undefined)
    const { initState, toggle, getState } = useToggleSwitch(toggleFn)
    initState('a', false)

    const result = await toggle('a', true)
    expect(result).toEqual({ ok: true })
    expect(toggleFn).toHaveBeenCalledWith('a', true)
    expect(getState('a').value).toBe(true)
  })

  it('toggle failure rolls back state and returns { ok: false }', async () => {
    const toggleFn = vi.fn().mockRejectedValue(new Error('fail'))
    const { initState, toggle, getState } = useToggleSwitch(toggleFn)
    initState('a', false)

    const result = await toggle('a', true)
    expect(result.ok).toBe(false)
    expect(result.code).toBe('fail')
    expect(getState('a').value).toBe(false) // rolled back
  })

  it('manages loading state during toggle', async () => {
    let resolve: (v: unknown) => void = () => {}
    const toggleFn = vi.fn().mockReturnValue(new Promise(r => { resolve = r }))
    const { initState, toggle, getState } = useToggleSwitch(toggleFn)
    initState('a', false)

    const promise = toggle('a', true)
    expect(getState('a').loading).toBe(true)

    resolve(undefined)
    await promise
    expect(getState('a').loading).toBe(false)
  })
})
