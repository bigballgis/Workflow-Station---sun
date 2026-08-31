import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import type { ListColumnMeta } from '@platform-shared/list/columnMeta'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

function column(overrides: Partial<ListColumnMeta> = {}): ListColumnMeta {
  return {
    field: 'title',
    label: 'Title',
    kind: 'TEXT',
    filterable: true,
    sortable: true,
    operators: ['contains', 'eq'],
    ...overrides,
  }
}

let wrapper: VueWrapper | null = null

function mountHeader(meta: ListColumnMeta) {
  wrapper = mount(ListColumnHeader, {
    props: { column: meta },
    global: { plugins: [ElementPlus] },
  })
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('shared ListColumnHeader', () => {
  it('renders a plain label (no dropdown) for a column that declares no capability', () => {
    const w = mountHeader(
      column({ filterable: false, sortable: false, operators: [] }),
    )
    expect(w.find('.el-dropdown').exists()).toBe(false)
    expect(w.find('.list-col-plain').text()).toBe('Title')
  })

  it('renders the dropdown trigger when the column declares at least one capability', () => {
    const w = mountHeader(column({ filterable: true, sortable: false }))
    expect(w.find('.el-dropdown').exists()).toBe(true)
    expect(w.find('.list-col-plain').exists()).toBe(false)
    expect(w.find('.list-col-label').text()).toBe('Title')
  })

  it('hides the resize handle unless a width is supplied', () => {
    expect(mountHeader(column()).find('.col-resize-handle').exists()).toBe(false)
    wrapper?.unmount()

    wrapper = mount(ListColumnHeader, {
      props: { column: column(), width: 180 },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.find('.col-resize-handle').exists()).toBe(true)
  })

  it('shows sort / filter icons on the trigger when those states are on', () => {
    wrapper = mount(ListColumnHeader, {
      props: {
        column: column(),
        sort: 'ASC',
        filtered: true,
      },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.find('.list-col-trigger').classes()).toContain('is-active-state')
    expect(wrapper.find('.list-col-state').exists()).toBe(true)
    expect(wrapper.findAll('.state-icon')).toHaveLength(2)
  })

  it('keeps the trigger idle when nothing is sorted or filtered', () => {
    const w = mountHeader(column())
    expect(w.find('.list-col-trigger').classes()).not.toContain('is-active-state')
    expect(w.find('.list-col-state').exists()).toBe(false)
  })

  it('does not expose grouped state or a group-change emit', () => {
    const w = mountHeader(column({ kind: 'ENUM', operators: ['eq', 'ne'] }))
    expect(Object.keys(w.props())).not.toContain('grouped')
    expect(w.emitted()).not.toHaveProperty('group-change')
  })
})
