import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useFormAutoSave } from '../useFormAutoSave'

/** Mirrors POLL_INTERVAL_MS / the scheduleAutoSave debounce in useFormAutoSave. */
const POLL_INTERVAL_MS = 3000
const SAVE_DEBOUNCE_MS = 5000

function mountAutoSave(options: Parameters<typeof useFormAutoSave>[0]) {
  const Host = defineComponent({
    setup() {
      const api = useFormAutoSave(options)
      api.setupAutoSavePolling()
      return () => null
    },
  })
  return mount(Host)
}

describe('useFormAutoSave', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('schedules auto-save when flush writes Validation+ onto the polled getRule()', async () => {
    vi.useFakeTimers()
    const rule = [{ field: 'name', type: 'input' }] as Array<Record<string, unknown>>
    let setupDone = false
    const flushPendingCanvasEdits = vi.fn(() => {
      if (!setupDone) return
      rule[0] = { field: 'name', type: 'input', validate: [{ mode: 'email', email: true }] }
    })
    const handleSaveForm = vi.fn().mockResolvedValue(undefined)
    const designerRef = ref({
      getRule: () => rule,
      getOption: () => ({}),
    })

    const wrapper = mountAutoSave({
      selectedForm: ref({ id: 1 }),
      designerRef,
      handleSaveForm,
      relationViewState: ref({}),
      t: (key: string) => key,
      autoSaving: ref(false),
      lastAutoSaveTime: ref(null),
      flushPendingCanvasEdits,
    })
    expect(flushPendingCanvasEdits).toHaveBeenCalled()
    expect(rule[0].validate).toBeUndefined()
    setupDone = true

    // One poll tick has to elapse before the flush runs and the change is noticed.
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS - 1)
    expect(rule[0].validate).toBeUndefined()

    await vi.advanceTimersByTimeAsync(1)
    expect(rule[0].validate).toEqual([{ mode: 'email', email: true }])
    expect(handleSaveForm).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(SAVE_DEBOUNCE_MS)
    expect(handleSaveForm).toHaveBeenCalledWith(false)
    wrapper.unmount()
  })

  it('detects Validation+ changes on the active designer, not only the main canvas', async () => {
    vi.useFakeTimers()
    const mainRule = [{ field: 'main' }] as Array<Record<string, unknown>>
    const subRule = [{ field: 'sub' }] as Array<Record<string, unknown>>
    let setupDone = false
    const flushPendingCanvasEdits = vi.fn(() => {
      if (!setupDone) return
      subRule[0] = { field: 'sub', validate: [{ mode: 'email', email: true }] }
    })
    const handleSaveForm = vi.fn().mockResolvedValue(undefined)
    const designerRef = ref({
      getRule: () => mainRule,
      getOption: () => ({}),
    })

    const wrapper = mountAutoSave({
      selectedForm: ref({ id: 1 }),
      designerRef,
      handleSaveForm,
      relationViewState: ref({}),
      t: (key: string) => key,
      autoSaving: ref(false),
      lastAutoSaveTime: ref(null),
      flushPendingCanvasEdits,
      getPollDesigner: () => ({
        getRule: () => subRule,
        getOption: () => ({}),
      }),
    })
    setupDone = true

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    expect(mainRule[0].validate).toBeUndefined()
    expect(subRule[0].validate).toEqual([{ mode: 'email', email: true }])

    await vi.advanceTimersByTimeAsync(SAVE_DEBOUNCE_MS)
    expect(handleSaveForm).toHaveBeenCalledWith(false)
    wrapper.unmount()
  })
})
