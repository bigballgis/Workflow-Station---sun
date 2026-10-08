import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useFormAutoSave } from '../useFormAutoSave'

const POLL_INTERVAL_MS = 3000

function mountDirtyTracker(options: Parameters<typeof useFormAutoSave>[0]) {
  const Host = defineComponent({
    setup() {
      const api = useFormAutoSave(options)
      api.setupAutoSavePolling()
      return api
    },
    template: '<div />',
  })
  return mount(Host)
}

describe('useFormAutoSave dirty tracking', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('clears dirty state when a designer property is restored to its saved value', async () => {
    vi.useFakeTimers()
    const rule = [{ field: 'name', title: 'Name' }] as Array<Record<string, unknown>>
    const wrapper = mountDirtyTracker({
      selectedForm: ref({ id: 1 }),
      designerRef: ref({ getRule: () => rule, getOption: () => ({}) }),
      relationViewState: ref({}),
    })

    rule[0] = { field: 'name', title: 'Display name' }
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    expect(wrapper.vm.isDirty).toBe(true)

    rule[0] = { field: 'name', title: 'Name' }
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    expect(wrapper.vm.isDirty).toBe(false)
    wrapper.unmount()
  })

  it('updates dirty state immediately when the designer emits a change event', () => {
    const rule = [{ field: 'name', title: 'Name' }] as Array<Record<string, unknown>>
    const wrapper = mountDirtyTracker({
      selectedForm: ref({ id: 1 }),
      designerRef: ref({ getRule: () => rule, getOption: () => ({}) }),
      relationViewState: ref({}),
    })

    rule[0] = { field: 'name', title: 'Display name' }
    wrapper.vm.refreshDirtyState()
    expect(wrapper.vm.isDirty).toBe(true)

    rule[0] = { field: 'name', title: 'Name' }
    wrapper.vm.refreshDirtyState()
    expect(wrapper.vm.isDirty).toBe(false)
    wrapper.unmount()
  })

  it('tracks relation-view changes against the same saved baseline', async () => {
    const relationViewState = ref({ customer: { visible: true } })
    const wrapper = mountDirtyTracker({
      selectedForm: ref({ id: 1 }),
      designerRef: ref({ getRule: () => [], getOption: () => ({}) }),
      relationViewState,
    })

    relationViewState.value.customer.visible = false
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.isDirty).toBe(true)

    relationViewState.value.customer.visible = true
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.isDirty).toBe(false)
    wrapper.unmount()
  })
})
