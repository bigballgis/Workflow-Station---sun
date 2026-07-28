import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import LookupField from '../LookupField.vue'

vi.mock('@/api/relationTable', () => ({
  relationTableApi: {
    searchForLookup: vi.fn().mockResolvedValue({ data: [] }),
    getViewFields: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../fetchLookupRowByPrimaryKey', () => ({
  fetchLookupRowByPrimaryKey: vi.fn().mockResolvedValue(null),
}))

const baseProps = {
  tableId: 1,
  searchFields: ['id', 'name'],
  displayField: 'name',
  selectedDisplayField: 'name',
  placeholder: 'Search',
}

describe('LookupField modelValue sync', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not emit clear when parent modelValue starts empty', async () => {
    const wrapper = mount(LookupField, {
      props: {
        ...baseProps,
        modelValue: null,
      },
      global: {
        stubs: {
          ElInput: true,
          ElIcon: true,
          ElTable: true,
          ElTableColumn: true,
          Teleport: true,
        },
      },
    })

    await flushPromises()
    await nextTick()

    expect(wrapper.emitted('clear')).toBeUndefined()
    wrapper.unmount()
  })

  it('shows persisted display field after async parent hydrate (e.g. Developer Tester)', async () => {
    const wrapper = mount(LookupField, {
      props: {
        ...baseProps,
        modelValue: null,
      },
      global: {
        stubs: {
          ElInput: true,
          ElIcon: true,
          ElTable: true,
          ElTableColumn: true,
          Teleport: true,
        },
      },
    })

    await flushPromises()
    expect(wrapper.emitted('clear')).toBeUndefined()

    await wrapper.setProps({
      modelValue: {
        id: 'u-42',
        name: 'Developer Tester',
      },
    })
    await nextTick()
    await flushPromises()

    expect(wrapper.emitted('clear')).toBeUndefined()
    expect(wrapper.find('.lookup-selected-text').text()).toBe('Developer Tester')
    wrapper.unmount()
  })

  it('still emits clear when user clicks the clear control', async () => {
    const wrapper = mount(LookupField, {
      props: {
        ...baseProps,
        modelValue: {
          id: 'u-42',
          name: 'Developer Tester',
        },
      },
      global: {
        stubs: {
          ElInput: true,
          ElIcon: true,
          ElTable: true,
          ElTableColumn: true,
          Teleport: true,
        },
      },
    })

    await flushPromises()
    await nextTick()

    const close = wrapper.find('.lookup-selected-close')
    expect(close.exists()).toBe(true)
    await close.trigger('click')
    await nextTick()

    expect(wrapper.emitted('clear')?.length).toBeGreaterThanOrEqual(1)
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBeNull()
    wrapper.unmount()
  })
})
