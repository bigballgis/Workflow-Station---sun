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
    document.body.innerHTML = ''
  })

  it('waits for the user to leave a property-panel input before saving its edit', async () => {
    vi.useFakeTimers()
    const rule = [{ field: 'name', type: 'input' }] as Array<Record<string, unknown>>
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
    })

    const panel = document.createElement('div')
    panel.className = '_fc-r'
    const input = document.createElement('input')
    panel.appendChild(input)
    document.body.appendChild(panel)
    input.focus()
    rule[0] = { field: 'name', type: 'input', title: 'Typing' }

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    await vi.advanceTimersByTimeAsync(SAVE_DEBOUNCE_MS)
    expect(document.activeElement).toBe(input)
    expect(handleSaveForm).not.toHaveBeenCalled()

    input.blur()
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    await vi.advanceTimersByTimeAsync(SAVE_DEBOUNCE_MS)
    expect(handleSaveForm).toHaveBeenCalledWith(false)
    wrapper.unmount()
  })

  it('detects a committed change on the active designer after the user leaves the field', async () => {
    vi.useFakeTimers()
    const mainRule = [{ field: 'main' }] as Array<Record<string, unknown>>
    const subRule = [{ field: 'sub' }] as Array<Record<string, unknown>>
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
      getPollDesigner: () => ({
        getRule: () => subRule,
        getOption: () => ({}),
      }),
    })

    // Mirrors the property input's blur/change commit after the user clicks elsewhere.
    subRule[0] = { field: 'sub', validate: [{ mode: 'email', email: true }] }

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    expect(mainRule[0].validate).toBeUndefined()
    expect(subRule[0].validate).toEqual([{ mode: 'email', email: true }])

    await vi.advanceTimersByTimeAsync(SAVE_DEBOUNCE_MS)
    expect(handleSaveForm).toHaveBeenCalledWith(false)
    wrapper.unmount()
  })
})
