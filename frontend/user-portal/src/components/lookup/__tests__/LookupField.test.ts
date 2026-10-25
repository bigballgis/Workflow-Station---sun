import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import LookupField from '../LookupField.vue'
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

vi.mock('@/api/relationTable', () => ({
  relationTableApi: {
    searchForLookup: vi.fn().mockResolvedValue({ data: [] }),
    getViewFields: vi.fn().mockResolvedValue([]),
    getFieldDefinitions: vi.fn().mockResolvedValue({ data: [{ fieldName: 'id', isPrimaryKey: true }] }),
  },
}))

vi.mock('../fetchLookupRowByPrimaryKey', () => ({
  fetchLookupRowByPrimaryKey: vi.fn().mockResolvedValue(null),
  resolveLookupPrimaryKeyField: vi.fn().mockResolvedValue('id'),
}))

const baseProps = {
  tableId: 1,
  searchFields: ['id', 'name'],
  displayField: 'name',
  selectedDisplayField: 'name',
  placeholder: 'Search',
}

describe('LookupField modelValue sync', () => {
  it('retries a failed identity request when the user focuses again', async () => {
    const { resolveLookupPrimaryKeyField } = await import('../fetchLookupRowByPrimaryKey')
    vi.mocked(resolveLookupPrimaryKeyField).mockRejectedValueOnce(new Error('temporary')).mockResolvedValue('id')
    const wrapper = mount(LookupField, {
      props: { ...baseProps, modelValue: null },
      global: { stubs: { ElInput: { template: '<input @focus="$emit(\'focus\')" />' }, ElIcon: true, ElTable: true, ElTableColumn: true, Teleport: true } },
    })
    await flushPromises()
    await wrapper.find('input').trigger('focus')
    await flushPromises()
    expect(resolveLookupPrimaryKeyField).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
  it('hydrates an id using the table primary key when search uses username', async () => {
    const { fetchLookupRowByPrimaryKey } = await import('../fetchLookupRowByPrimaryKey')
    vi.mocked(fetchLookupRowByPrimaryKey).mockResolvedValueOnce({ id: 'person-42', username: 'Alice' })
    const wrapper = mount(LookupField, {
      props: { ...baseProps, searchFields: ['username'], selectedDisplayField: 'username', modelValue: 'person-42' },
      global: { stubs: { ElInput: true, ElIcon: true, ElTable: true, ElTableColumn: true, Teleport: true } },
    })
    await flushPromises()
    expect(fetchLookupRowByPrimaryKey).toHaveBeenLastCalledWith(1, 'person-42', expect.not.objectContaining({ primaryKeyField: 'username' }))
    expect(wrapper.text()).toContain('Alice')
    wrapper.unmount()
  })
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

describe('LookupField autoClearIfMissing with INACTIVE rows', () => {
  const stubs = {
    ElInput: true,
    ElIcon: true,
    ElTable: true,
    ElTableColumn: true,
    Teleport: true,
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  async function changeFilterWithPkRecheck(pkRecheckRows: Record<string, unknown>[]) {
    const { relationTableApi } = await import('@/api/relationTable')
    // Dropdown rows exclude INACTIVE rows; only the includeInactive PK re-check can see them.
    vi.mocked(relationTableApi.searchForLookup).mockImplementation(async (_tableId, params) => ({
      data: params.includeInactive ? pkRecheckRows : [],
    }) as never)
    const wrapper = mount(LookupField, {
      props: {
        ...baseProps,
        autoClearIfMissing: true,
        filterConditions: [{ fieldName: 'region', value: 'A', matchType: 'eq' }],
        modelValue: { id: 'r-1', name: 'Saved row' },
      },
      global: { stubs },
    })
    await flushPromises()
    await wrapper.setProps({ filterConditions: [{ fieldName: 'region', value: 'B', matchType: 'eq' }] })
    await flushPromises()
    return { wrapper, relationTableApi }
  }

  it('keeps a saved selection whose row is INACTIVE but still matches the filter', async () => {
    const { wrapper, relationTableApi } = await changeFilterWithPkRecheck([{ id: 'r-1', name: 'Saved row' }])

    expect(relationTableApi.searchForLookup).toHaveBeenCalledWith(1, expect.objectContaining({
      includeInactive: true,
      filterConditions: [
        { fieldName: 'region', value: 'B', matchType: 'eq' },
        { fieldName: 'id', value: 'r-1', matchType: 'eq' },
      ],
    }))
    expect(wrapper.emitted('clear')).toBeUndefined()
    wrapper.unmount()
  })

  it('still clears a saved selection that no longer matches the filter', async () => {
    const { wrapper } = await changeFilterWithPkRecheck([])

    expect(wrapper.emitted('clear')?.length).toBeGreaterThanOrEqual(1)
    wrapper.unmount()
  })
})
