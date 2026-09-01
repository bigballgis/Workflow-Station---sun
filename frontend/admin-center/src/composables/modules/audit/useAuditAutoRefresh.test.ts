import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { useAuditAutoRefresh } from './useAuditAutoRefresh'

function mountRefresh(run: () => void, shouldSkip = () => false) {
  return mount(defineComponent({
    setup() {
      return useAuditAutoRefresh({ intervalSeconds: 5, shouldSkip, run })
    },
    template: '<div />',
  }))
}

describe('useAuditAutoRefresh', () => {
  afterEach(() => {
    vi.useRealTimers()
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
  })

  it('does not tick while the document is hidden', () => {
    vi.useFakeTimers()
    const run = vi.fn()
    const wrapper = mountRefresh(run)
    wrapper.vm.startAutoRefresh()
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => true })
    document.dispatchEvent(new Event('visibilitychange'))
    vi.advanceTimersByTime(10_000)
    expect(run).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
