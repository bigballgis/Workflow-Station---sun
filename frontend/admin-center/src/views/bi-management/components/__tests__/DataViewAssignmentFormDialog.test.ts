import { defineComponent, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Dialog from '../DataViewAssignmentFormDialog.vue'
import { biManagementApi, type DataViewAssignmentResponse } from '@/api/biManagement'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/utils/notify', () => ({ notifyError: vi.fn(), notifySuccess: vi.fn() }))
vi.mock('@/api/biManagement', () => ({ biManagementApi: {
  dashboard: { list: vi.fn() },
  dataViewAssignment: { listFunctionUnits: vi.fn(), listTables: vi.fn(), listViews: vi.fn(), update: vi.fn(), createBatch: vi.fn() },
} }))

const row = (id: number, enabled: boolean) => ({
  id: String(id), dashboardId: `dashboard-${id}`, functionUnitCode: `FU_${id}`,
  tableName: `table_${id}`, viewName: `View ${id}`, viewAvailable: true, enabled,
}) as DataViewAssignmentResponse
const Form = defineComponent({
  setup(_, { expose }) { expose({ validate: async () => true }) },
  template: '<form><slot /></form>',
})
const Button = defineComponent({ props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' })
function mountDialog() {
  return mount(Dialog, { props: { modelValue: false, mode: 'edit', initialRow: row(1, false) },
    global: { directives: { loading: () => {} }, stubs: {
      ElDialog: { template: '<div><slot /><slot name="footer" /></div>' }, ElForm: Form,
      ElFormItem: { template: '<div><slot /></div>' }, ElButton: Button,
      ElSelect: true, ElOption: true, ElSwitch: true, ElAlert: true,
    } },
  })
}
type State = {
  form: { viewName: string | null; enabled: boolean }
  dashboards: { id: string }[]
  functionUnits: { code: string }[]
  submit(): Promise<void>
  initialize(): Promise<void>
}

describe('Data View assignment initialization isolation (#1722)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(biManagementApi.dashboard.list).mockResolvedValue({ content: [], totalElements: 0 } as never)
    vi.mocked(biManagementApi.dataViewAssignment.listFunctionUnits).mockResolvedValue([])
    vi.mocked(biManagementApi.dataViewAssignment.listTables).mockResolvedValue([])
    vi.mocked(biManagementApi.dataViewAssignment.listViews).mockResolvedValue([])
    vi.mocked(biManagementApi.dataViewAssignment.update).mockResolvedValue(row(2, true))
    vi.mocked(biManagementApi.dataViewAssignment.createBatch).mockResolvedValue([row(2, false)])
  })

  it('never submits previous record values when opening the next record fails, and can retry', async () => {
    const wrapper = mountDialog()
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    await wrapper.setProps({ modelValue: false })
    vi.mocked(biManagementApi.dashboard.list).mockRejectedValueOnce(new Error('unavailable'))
    await wrapper.setProps({ initialRow: row(2, true), modelValue: true }); await flushPromises()
    const state = wrapper.vm as unknown as State
    expect(state.form.viewName).toBe('View 2')
    expect(state.form.enabled).toBe(true)
    expect(wrapper.findAll('button').find(b => b.text() === 'common.confirm')?.attributes('disabled')).toBeDefined()
    await state.submit()
    expect(biManagementApi.dataViewAssignment.update).not.toHaveBeenCalled()
    await state.initialize(); await state.submit()
    expect(biManagementApi.dataViewAssignment.update).toHaveBeenCalledWith('2', expect.objectContaining({ functionUnitCode: 'FU_2', viewName: 'View 2' }))
    expect(vi.mocked(biManagementApi.dataViewAssignment.update).mock.calls[0][1]).not.toHaveProperty('enabled')
    wrapper.unmount()
  })

  it('blocks saving until dependent table and View options have loaded successfully', async () => {
    vi.mocked(biManagementApi.dataViewAssignment.listViews).mockRejectedValueOnce(new Error('unavailable'))
    const wrapper = mountDialog()
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    await (wrapper.vm as unknown as State).submit()
    expect(biManagementApi.dataViewAssignment.update).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('discards a late initialization response after close and reopening another record', async () => {
    let complete!: (value: never) => void
    vi.mocked(biManagementApi.dashboard.list).mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
      .mockResolvedValueOnce({ content: [{ id: 'dashboard-2' }] } as never)
    vi.mocked(biManagementApi.dataViewAssignment.listFunctionUnits)
      .mockResolvedValueOnce([{ code: 'FU_1' }] as never).mockResolvedValueOnce([{ code: 'FU_2' }] as never)
    const wrapper = mountDialog()
    await wrapper.setProps({ modelValue: true }); await nextTick()
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true, initialRow: row(2, true) }); await flushPromises()
    complete({ content: [{ id: 'dashboard-1' }] } as never); await flushPromises()
    const state = wrapper.vm as unknown as State
    expect(state.form.viewName).toBe('View 2')
    expect(state.form.enabled).toBe(true)
    expect(state.dashboards.map(d => d.id)).toEqual(['dashboard-2'])
    expect(state.functionUnits.map(u => u.code)).toEqual(['FU_2'])
    await state.submit()
    expect(biManagementApi.dataViewAssignment.update).toHaveBeenCalledWith('2', expect.objectContaining({ functionUnitCode: 'FU_2', viewName: 'View 2' }))
    wrapper.unmount()
  })

  it('still creates configured disabled assignments after successful initialization', async () => {
    const wrapper = mountDialog()
    await wrapper.setProps({ mode: 'create', initialRow: null, modelValue: true }); await flushPromises()
    const state = wrapper.vm as unknown as State
    Object.assign(state.form, {
      functionUnitCode: 'FU_2', tableName: 'table_2', viewName: 'View 2', dashboardIds: ['dashboard-2'], enabled: false,
    })
    await state.submit()
    // The View is named by unit code and View name; the table only narrows the choice.
    expect(biManagementApi.dataViewAssignment.createBatch).toHaveBeenCalledWith({
      functionUnitCode: 'FU_2', viewName: 'View 2', dashboardIds: ['dashboard-2'], enabled: false,
    })
    expect(wrapper.emitted('success')).toHaveLength(1)
    wrapper.unmount()
  })
})
