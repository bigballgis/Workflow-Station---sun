import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { permissionApi } from '@/api/permission'
import AssignmentEditor from '../SubTableBatchAssignmentEditor.vue'
import type { SubTableBatchCapability } from '@/utils/subTableBatchCapability'
import type { BatchUniformAssignmentState } from '@/utils/subTableBatchAssignmentDraft'

const requestPost = vi.hoisted(() => vi.fn())

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/request', () => ({ request: { get: vi.fn(), post: requestPost } }))
vi.mock('@/api/permission', () => ({ permissionApi: { getBusinessUnitsTree: vi.fn(), getBusinessUnitRoles: vi.fn(), getAvailableRoles: vi.fn() } }))
const capability: SubTableBatchCapability = {
  batchControlId: 'assignment-grid', bindingId: '1', originalCrud: {},
  capabilities: { allowBatchAdd: false, allowBatchEdit: true, allowBatchDelete: false, allowBatchAssign: true },
  assignmentConfig: { allowUser: true, allowRole: true, assigneeField: 'handler', roleField: 'pool', buField: 'org' },
  batchForm: { schemaVersion: 1, rule: [{ type: 'miAssignment' }] },
}
type State = {
  uniformRoleOptions: Array<{ value: string }>
  uniform: BatchUniformAssignmentState
  collect(rows: Array<Record<string, unknown>>): { rows: Array<Record<string, unknown>>; change?: unknown }
  hasChanges(): boolean; selectUniformBusinessUnit(value: string): Promise<void>
  userOptionsFor(key: string, selectedId: string): Array<{ id: string; name: string }>
}
function editor(rows: Array<Record<string, unknown>>) {
  const wrapper = mount(AssignmentEditor, { props: { rows, capability, rowIdentity: row => String(row.key), rowScopeKey: row => ({ key: row.key }) },
    global: { stubs: { ElRow: { template: '<div><slot/></div>' }, ElCol: { template: '<div><slot/></div>' }, ElTable: true, ElTableColumn: true, ElForm: { template: '<div><slot/></div>' }, ElFormItem: { template: '<div><slot/></div>', methods: { validate: () => Promise.resolve() } }, ElDivider: true,
      ElPagination: true, ElSelect: true, ElOption: true, ElAlert: true, ElCascader: true } } })
  return { wrapper, state: wrapper.findComponent({ name: 'BatchAssignmentEditor' }).vm as unknown as State }
}
describe('embedded assignment editor', () => {
  it('renders a real person picker for an existing Assignment marker without children', async () => {
    const { wrapper } = editor([{ key: '1' }])
    await wrapper.findAll('[role="radio"]')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'BatchAssignmentInput' })).toHaveLength(1)
    expect(wrapper.findComponent({ name: 'BatchAssignmentInput' }).props('rule').field).toBe('handler')
    expect(wrapper.findComponent({ name: 'BatchAssignmentInput' }).find('el-select-stub').exists()).toBe(true)
    wrapper.unmount()
  })
  it('renders BU and Role for an existing marker, with Role disabled until BU is chosen', async () => {
    const { wrapper } = editor([{ key: '1' }])
    await wrapper.findAll('[role="radio"]')[1]!.trigger('click')
    await flushPromises()
    const controls = wrapper.findAllComponents({ name: 'BatchAssignmentInput' })
    expect(controls.map(control => control.props('rule').field)).toEqual(['org', 'pool'])
    expect(controls[1]!.find('el-select-stub').attributes('disabled')).toBe('true')
    wrapper.unmount()
  })
  it('hydrates only the chosen uniform user and keeps existing assignments unchanged by default', async () => {
    requestPost.mockResolvedValue({ data: [{ id: 'user-A', username: 'alice', name: 'Alice Example' }] })
    const rows = [{ key: '1', handler: 'user-A' }, { key: '2', handler: 'user-A' }]
    const { wrapper, state } = editor(rows)
    await flushPromises()
    expect(requestPost).not.toHaveBeenCalled()
    expect(state.hasChanges()).toBe(false)
    expect(state.collect(rows)).toEqual({ rows })
    state.uniform.mode = 'person'; state.uniform.assigneeId = 'user-A'
    await flushPromises()
    expect(state.userOptionsFor('uniform', 'user-A')[0]?.name).toBe('Alice Example')
    expect(requestPost).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('restores an existing BU+Role without declaring a change, coalescing same-BU queries', async () => {
    vi.mocked(permissionApi.getBusinessUnitsTree).mockResolvedValue({ data: [{ id: 'query-A', code: 'org-A', name: 'A' }] } as never)
    vi.mocked(permissionApi.getBusinessUnitRoles).mockResolvedValue({ data: [{ code: 'pool-A', name: 'A' }] } as never)
    const rows = [{ key: '1', handler: null, org: 'org-A', pool: 'pool-A' }, { key: '2', handler: null, org: 'org-A', pool: 'pool-A' }]
    const { wrapper, state } = editor(rows)
    await flushPromises()
    expect(permissionApi.getBusinessUnitRoles).not.toHaveBeenCalled()
    expect(state.hasChanges()).toBe(false)
    expect(state.collect(rows)).toEqual({ rows })
    await wrapper.setProps({ rows: [{ ...rows[0]!, label: 'ordinary edit' }, rows[1]!] })
    expect(state.uniform.mode).toBe('none')
    wrapper.unmount()
  })
  it('rejects the first A response after A→B→A, retaining only the latest generation', async () => {
    vi.mocked(permissionApi.getBusinessUnitsTree).mockResolvedValue({ data: [{ id: 'A', code: 'org-A', name: 'A' }, { id: 'B', code: 'org-B', name: 'B' }] } as never)
    const rows = [{ key: '1' }]
    const { wrapper, state } = editor(rows)
    await flushPromises()
    let resolveFirst!: (value: unknown) => void
    vi.mocked(permissionApi.getBusinessUnitRoles).mockImplementationOnce(() => new Promise(resolve => { resolveFirst = value => resolve(value as never) }))
    vi.mocked(permissionApi.getBusinessUnitRoles).mockResolvedValueOnce({ data: [{ code: 'B-pool' }] } as never)
    vi.mocked(permissionApi.getBusinessUnitRoles).mockResolvedValueOnce({ data: [{ code: 'latest-A' }] } as never)
    const first = state.selectUniformBusinessUnit('A')
    await state.selectUniformBusinessUnit('B')
    await state.selectUniformBusinessUnit('A')
    resolveFirst({ data: [{ code: 'obsolete-A' }] }); await first
    expect(state.uniformRoleOptions.map(role => role.value)).toEqual(['latest-A'])
    wrapper.unmount()
  })
})
