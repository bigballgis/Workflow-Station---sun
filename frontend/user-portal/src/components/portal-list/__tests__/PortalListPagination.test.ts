import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import PortalListPagination from '../PortalListPagination.vue'

function mountPager(props: Record<string, unknown> = {}) {
  return mount(PortalListPagination, {
    props: { currentPage: 1, pageSize: 20, total: 100, ...props },
    global: { plugins: [ElementPlus] },
  })
}

describe('PortalListPagination', () => {
  it('emits one change per page click', async () => {
    const wrapper = mountPager()
    await wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 3)

    expect(wrapper.emitted('update:currentPage')).toEqual([[3]])
    expect(wrapper.emitted('change')).toHaveLength(1)
  })

  it('emits one change for a size change even when the pager also clamps its page', async () => {
    const wrapper = mountPager({ currentPage: 5, pageSize: 10 })
    const pager = wrapper.findComponent({ name: 'ElPagination' })

    // Element Plus dispatches size-change and then its own current-change.
    await pager.vm.$emit('size-change', 50)
    await pager.vm.$emit('current-change', 2)

    expect(wrapper.emitted('update:pageSize')).toEqual([[50]])
    expect(wrapper.emitted('update:currentPage')).toEqual([[1]])
    expect(wrapper.emitted('change')).toHaveLength(1)
  })

  it('accepts page clicks again after the size change settles', async () => {
    const wrapper = mountPager({ currentPage: 5, pageSize: 10 })
    const pager = wrapper.findComponent({ name: 'ElPagination' })

    await pager.vm.$emit('size-change', 50)
    await nextTick()
    await pager.vm.$emit('current-change', 2)

    expect(wrapper.emitted('change')).toHaveLength(2)
    expect(wrapper.emitted('update:currentPage')).toEqual([[1], [2]])
  })

  it('pulls the caller back once when the current page no longer exists', async () => {
    const wrapper = mountPager({ currentPage: 5, pageSize: 20, total: 100 })

    await wrapper.setProps({ total: 40 })

    expect(wrapper.emitted('update:currentPage')).toEqual([[2]])
    expect(wrapper.emitted('change')).toHaveLength(1)
  })

  it('never hands the caller a page beyond the last one', async () => {
    const wrapper = mountPager({ currentPage: 1, pageSize: 20, total: 40 })

    await wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 9)

    expect(wrapper.emitted('update:currentPage')).toEqual([[2]])
  })

  it('keeps the pager mounted by default and drops it only when asked', async () => {
    const shown = mountPager({ total: 0 })
    expect(shown.find('.portal-list-pagination').exists()).toBe(true)

    const hidden = mountPager({ total: 0, hideWhenEmpty: true })
    expect(hidden.find('.portal-list-pagination').exists()).toBe(false)

    await hidden.setProps({ total: 5 })
    expect(hidden.find('.portal-list-pagination').exists()).toBe(true)
  })
})
