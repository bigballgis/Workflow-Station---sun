import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ListPagination from '@platform-shared/list/ListPagination.vue'

let wrapper: VueWrapper | null = null

function mountPagination(props: Partial<{ page: number; size: number; total: number }> = {}) {
  wrapper = mount(ListPagination, {
    props: { page: 1, size: 20, total: 200, ...props },
    global: { plugins: [ElementPlus] },
  })
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('shared ListPagination', () => {
  it('clicking next page emits update:page and a single change payload', async () => {
    const w = mountPagination()
    await w.find('.btn-next').trigger('click')

    expect(w.emitted('update:page')).toEqual([[2]])
    expect(w.emitted('change')).toEqual([[{ page: 2, size: 20 }]])
    expect(w.emitted('update:size')).toBeUndefined()
  })

  it('size change resets to page 1 and fires change exactly once', async () => {
    const w = mountPagination({ page: 5 })
    const pagination = w.findComponent({ name: 'ElPagination' })

    pagination.vm.$emit('size-change', 50)
    // el-pagination clamps the now-out-of-range page in the same tick — must be swallowed.
    pagination.vm.$emit('current-change', 1)
    await nextTick()

    expect(w.emitted('update:size')).toEqual([[50]])
    expect(w.emitted('update:page')).toEqual([[1]])
    expect(w.emitted('change')).toEqual([[{ page: 1, size: 50 }]])
  })

  it('page navigation after a size change works again (microtask guard released)', async () => {
    const w = mountPagination({ page: 1 })
    const pagination = w.findComponent({ name: 'ElPagination' })

    pagination.vm.$emit('size-change', 10)
    await nextTick()
    pagination.vm.$emit('current-change', 3)
    await nextTick()

    expect(w.emitted('change')).toEqual([
      [{ page: 1, size: 10 }],
      [{ page: 3, size: 20 }],
    ])
  })

  it('renders the uniform layout with total/sizes/jumper', () => {
    const w = mountPagination()
    expect(w.find('.el-pagination__total').exists()).toBe(true)
    expect(w.find('.el-pagination__sizes').exists()).toBe(true)
    expect(w.find('.el-pagination__jump').exists()).toBe(true)
  })
})
