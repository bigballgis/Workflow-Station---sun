import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { permissionApi } from '@/api/permission'
import AssignmentEditor from '../SubTableBatchAssignmentEditor.vue'
import type { SubTableBatchCapability } from '@/utils/subTableBatchCapability'
import type { BatchAssignmentState, BatchUniformAssignmentState } from '@/utils/subTableBatchAssignmentDraft'

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
  rowIntent: Record<string, BatchAssignmentState>; roleOptions: Record<string, Array<{ value: string }>>
  uniform: BatchUniformAssignmentState
  collect(rows: Array<Record<string, unknown>>): { rows: Array<Record<string, unknown>>; change?: unknown }
  hasChanges(): boolean; selectBusinessUnit(key: string, value: string): Promise<void>
  userOptionsFor(key: string, selectedId: string): Array<{ id: string; name: string }>
}
function editor(rows: Array<Record<string, unknown>>) {
  const wrapper = mount(AssignmentEditor, { props: { rows, capability, rowIdentity: row => String(row.key), rowScopeKey: row => ({ key: row.key }) },
    global: { stubs: { ElTable: true, ElTableColumn: true, ElForm: { template: '<div><slot/></div>' }, ElFormItem: true, ElDivider: true,
      ElPagination: true, ElSelect: true, ElOption: true, ElAlert: true, ElCascader: true } } })
  return { wrapper, state: wrapper.findComponent({ name: 'BatchAssignmentEditor' }).vm as unknown as State }
}
describe('embedded assignment editor', () => {
  it('hydrates stored user IDs together and retains names across searches and uniform assignment', async () => {
    requestPost.mockResolvedValue({ data: [{ id: 'user-A', username: 'alice', name: 'Alice Example' }] })
    const rows = [{ key: '1', handler: 'user-A' }, { key: '2', handler: 'user-A' }]
    const { wrapper, state } = editor(rows)
    await flushPromises()
    expect(requestPost).toHaveBeenCalledWith('/tasks/users/summaries', { userIds: ['user-A'] }, expect.anything())
    expect(state.userOptionsFor('row:1', 'user-A')).toContainEqual(expect.objectContaining({ id: 'user-A', name: 'Alice Example' }))
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
    expect(state.rowIntent['1']?.businessUnitId).toBe('query-A')
    expect(state.roleOptions['1']).toEqual([{ value: 'pool-A', label: 'A' }])
    expect(permissionApi.getBusinessUnitRoles).toHaveBeenCalledTimes(1)
    expect(state.hasChanges()).toBe(false)
    expect(state.collect(rows)).toEqual({ rows })
    await wrapper.setProps({ rows: [{ ...rows[0]!, label: 'ordinary edit' }, rows[1]!] })
    expect(state.rowIntent['1']?.businessUnitId).toBe('query-A')
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
    const first = state.selectBusinessUnit('1', 'A')
    await state.selectBusinessUnit('1', 'B')
    await state.selectBusinessUnit('1', 'A')
    resolveFirst({ data: [{ code: 'obsolete-A' }] }); await first
    expect(state.roleOptions['1']?.map(role => role.value)).toEqual(['latest-A'])
    wrapper.unmount()
  })
})
