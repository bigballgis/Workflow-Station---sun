import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import dayjs from 'dayjs'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getChangeHistory } from '@/api/processForm'
import { useChangeHistoryLoader, type ChangeHistoryLoader } from '../useChangeHistoryLoader'
vi.mock('@/api/processForm', () => ({
  getChangeHistory: vi.fn(),
}))
const t = ((key: string) => key) as never
describe('useChangeHistoryLoader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })
  it('loads immediately and keeps loading visible when the panel mounts', async () => {
    let resolveRequest: ((value: { data: unknown[] }) => void) | undefined
    vi.mocked(getChangeHistory).mockReturnValue(new Promise(resolve => {
      resolveRequest = resolve
    }) as never)
    let loader: ChangeHistoryLoader | undefined
    const Host = defineComponent({
      setup() {
        loader = useChangeHistoryLoader({ processInstanceId: 'process-1' }, t, dayjs)
        return () => h('div')
      },
    })
    const wrapper = mount(Host)
    expect(loader?.loading.value).toBe(true)
    expect(getChangeHistory).toHaveBeenCalledOnce()
    expect(getChangeHistory).toHaveBeenCalledWith('process-1', undefined, undefined)
    resolveRequest?.({ data: [] })
    await flushPromises()
    expect(loader?.loading.value).toBe(false)
    expect(loader?.records.value).toEqual([])
    wrapper.unmount()
  })
  it('does not show loading or request data without a process instance', () => {
    let loader: ChangeHistoryLoader | undefined
    const Host = defineComponent({
      setup() {
        loader = useChangeHistoryLoader({ processInstanceId: '' }, t, dayjs)
        return () => h('div')
      },
    })
    const wrapper = mount(Host)
    expect(loader?.loading.value).toBe(false)
    expect(getChangeHistory).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('filters later records of the same task before grouping completed snapshots', async () => {
    vi.mocked(getChangeHistory).mockResolvedValue({ data: [
      { id: 1, taskInstanceId: 'task', timestamp: '2026-10-23T10:00:00Z', newValue: 'visible' },
      { id: 2, taskInstanceId: 'task', timestamp: '2026-10-23T10:00:02Z', newValue: 'later' },
    ] } as never)
    let loader: ChangeHistoryLoader | undefined
    const wrapper = mount(defineComponent({ setup() {
      loader = useChangeHistoryLoader({ processInstanceId: 'process', taskInstanceId: 'task',
        snapshotTime: '2026-10-23T10:00:01Z' }, t, dayjs)
      return () => h('div')
    } }))
    await flushPromises()
    expect(loader!.records.value.map(row => row.id)).toEqual([1])
    wrapper.unmount()
  })

})