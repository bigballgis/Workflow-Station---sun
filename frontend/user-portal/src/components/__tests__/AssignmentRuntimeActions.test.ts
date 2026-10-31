import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
const mocks = vi.hoisted(() => ({ availability: vi.fn(), reopen: vi.fn(), handoff: vi.fn(), push: vi.fn(), go: vi.fn() }))
vi.mock('@/api/assignmentRuntimeActions', () => ({ actionAvailability: mocks.availability, reopenProcess: mocks.reopen, handoffTask: mocks.handoff }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push, go: mocks.go }) }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
import AssignmentReopenAction from '@/components/AssignmentReopenAction.vue'
import AssignmentHandoffAction from '@/components/tasks/AssignmentHandoffAction.vue'
let wrapper: VueWrapper | undefined
type Confirmation = Awaited<ReturnType<typeof ElMessageBox.confirm>>
beforeEach(() => { vi.resetAllMocks(); sessionStorage.clear(); localStorage.clear(); mocks.push.mockResolvedValue(undefined) })
afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.restoreAllMocks() })

describe('reopen uses a durable request and explicit qualification', () => {
  it('cannot confirm one source and reopen a different source after the props change', async () => {
    mocks.availability.mockResolvedValueOnce({ allowed: true, targets: [] }).mockResolvedValueOnce({ allowed: false, targets: [] })
    let confirm!: (value: Confirmation) => void
    vi.spyOn(ElMessageBox, 'confirm').mockImplementation(() => new Promise(resolve => { confirm = resolve }))
    wrapper = mount(AssignmentReopenAction, { props: { processId: 'source-a', status: 'COMPLETED' }, global: { plugins: [ElementPlus] } })
    await flushPromises(); await wrapper.get('[data-test="assignment-reopen"]').trigger('click')
    await wrapper.setProps({ processId: 'source-b' }); await flushPromises()
    confirm('confirm' as Confirmation); await flushPromises()
    expect(mocks.reopen).not.toHaveBeenCalled()
    expect(sessionStorage.length).toBe(0)
  })
  it('does not offer a new reopen when eligibility is false or unavailable', async () => {
    mocks.availability.mockResolvedValue({ allowed: false, targets: [] })
    wrapper = mount(AssignmentReopenAction, { props: { processId: 'source', status: 'COMPLETED' }, global: { plugins: [ElementPlus] } })
    await flushPromises(); expect(wrapper.find('[data-test="assignment-reopen"]').exists()).toBe(false)
    expect(mocks.reopen).not.toHaveBeenCalled()
  })
  it('reuses its UUID after unknown delivery, a page remount and a pending synchronization response', async () => {
    mocks.availability.mockResolvedValue({ allowed: true, targets: [] })
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as Confirmation)
    mocks.reopen.mockRejectedValueOnce(new Error('synthetic lost response'))
    const props = { processId: 'source', status: 'COMPLETED' }
    wrapper = mount(AssignmentReopenAction, { props, global: { plugins: [ElementPlus] } })
    await flushPromises(); await wrapper.get('[data-test="assignment-reopen"]').trigger('click'); await flushPromises()
    const operation = mocks.reopen.mock.calls[0][1]
    expect(operation).toMatch(/^[0-9a-f-]{36}$/i)
    wrapper.unmount()
    mocks.availability.mockResolvedValue({ allowed: false, targets: [] })
    wrapper = mount(AssignmentReopenAction, { props, global: { plugins: [ElementPlus] } }); await flushPromises()
    mocks.reopen.mockResolvedValueOnce({ operationId: operation, synchronizationState: 'PENDING_SYNC', processInstanceId: 'new-round', processDefinitionId: 'frozen' })
    await wrapper.get('[data-test="assignment-reopen"]').trigger('click'); await flushPromises()
    expect(mocks.reopen.mock.calls[1]).toEqual(['source', operation]); expect(mocks.push).not.toHaveBeenCalled()
    mocks.reopen.mockResolvedValueOnce({ operationId: operation, synchronizationState: 'READY', processInstanceId: 'new-round', processDefinitionId: 'frozen' })
    await wrapper.get('[data-test="assignment-reopen"]').trigger('click'); await flushPromises()
    expect(mocks.reopen.mock.calls[2]).toEqual(['source', operation])
    expect(mocks.push).toHaveBeenCalledWith({ name: 'ApplicationDetail', params: { id: 'new-round' } })
    expect(mocks.go).toHaveBeenCalledWith(0)
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
  })
})
describe('one-use handoff offers only server-qualified recipients', () => {
  it('submits the selected qualified ID, then leaves the read-only task', async () => {
    mocks.availability.mockResolvedValue({ allowed: true, targets: [{ id: 'eligible-id', label: 'Eligible colleague' }] })
    mocks.handoff.mockResolvedValue({ success: true })
    wrapper = mount(AssignmentHandoffAction, { props: { taskId: 'task', enabled: true }, global: { plugins: [ElementPlus] } })
    await flushPromises(); await wrapper.get('[data-test="assignment-handoff"]').trigger('click'); await flushPromises()
    expect(wrapper.get('[data-test="handoff-confirm"]').attributes('disabled')).toBeDefined()
    wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'eligible-id'); await flushPromises()
    await wrapper.get('[data-test="handoff-confirm"]').trigger('click'); await flushPromises()
    expect(mocks.handoff).toHaveBeenCalledWith('task', 'eligible-id'); expect(mocks.push).toHaveBeenCalledWith('/tasks')
  })
  it('does not offer an action for an unprotected task', async () => {
    wrapper = mount(AssignmentHandoffAction, { props: { taskId: 'task', enabled: false }, global: { plugins: [ElementPlus] } })
    await flushPromises(); expect(mocks.availability).not.toHaveBeenCalled(); expect(wrapper.find('[data-test="assignment-handoff"]').exists()).toBe(false)
  })
  it('retains no recipient action after a failed eligibility read', async () => {
    mocks.availability.mockRejectedValue(new Error('synthetic unavailable'))
    wrapper = mount(AssignmentHandoffAction, { props: { taskId: 'task', enabled: true }, global: { plugins: [ElementPlus] } })
    await flushPromises(); expect(wrapper.find('[data-test="assignment-handoff"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('checkUnavailable'); expect(mocks.handoff).not.toHaveBeenCalled()
  })
})
