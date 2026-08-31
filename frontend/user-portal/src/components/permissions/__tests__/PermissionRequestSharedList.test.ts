import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import PermissionRequestSharedList from '@/components/permissions/PermissionRequestSharedList.vue'
import { permissionApi } from '@/api/permission'
import type { ListColumnMeta } from '@platform-shared/list/columnMeta'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/permission', () => ({
  permissionApi: {
    queryPermissionRequests: vi.fn(),
  },
}))
vi.mock('@/composables/list/searchListFilterUsers', () => ({
  searchListFilterUsers: vi.fn(async () => []),
}))

const api = vi.mocked(permissionApi.queryPermissionRequests)

const COLUMNS: ListColumnMeta[] = [
  { field: 'requestType', label: 'permission.requestType', kind: 'ENUM', filterable: true, sortable: true, operators: ['eq'], options: [{ value: 'ROLE', label: 'ROLE' }] },
  { field: 'status', label: 'permission.status', kind: 'ENUM', filterable: true, sortable: true, operators: ['eq'], options: [{ value: 'PENDING', label: 'PENDING' }] },
  { field: 'createdAt', label: 'common.createdAt', kind: 'DATETIME', filterable: true, sortable: true, operators: ['between'] },
]

let wrapper: VueWrapper | null = null

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  api.mockResolvedValue({
    data: { columns: COLUMNS, content: [], totalElements: 0, page: 0, size: 20 },
  } as never)
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('PermissionRequestSharedList', () => {
  it('loads via /permissions/requests/query for the given scope', async () => {
    wrapper = mount(PermissionRequestSharedList, {
      props: {
        scope: 'MY_PENDING',
        storageKey: 'test-perm-my-pending',
        emptyText: 'empty',
        enabled: true,
      },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(api).toHaveBeenCalled()
    expect(api.mock.calls[0][0].scope).toBe('MY_PENDING')
    expect(wrapper!.findAllComponents({ name: 'ListColumnHeader' }).length).toBeGreaterThan(0)
  })

  it('renders Member / Leader on pending approval rows', async () => {
    api.mockResolvedValue({
      data: {
        columns: [
          { field: 'applicantId', label: 'permission.beneficiaryColumn', kind: 'USER', filterable: true, sortable: true, groupable: true, operators: ['eq'] },
          { field: 'submittedByUserId', label: 'permission.submittedByColumn', kind: 'USER', filterable: true, sortable: true, groupable: true, operators: ['eq'] },
          { field: 'requestType', label: 'permission.requestType', kind: 'ENUM', filterable: true, sortable: true, groupable: true, operators: ['eq'], options: [] },
          { field: 'targetName', label: 'permission.requestTarget', kind: 'TEXT', filterable: true, sortable: true, groupable: false, operators: ['contains'] },
          { field: 'membershipType', label: 'permission.membershipType', kind: 'ENUM', filterable: true, sortable: true, groupable: true, operators: ['eq'], options: [] },
          { field: 'reason', label: 'permission.reason', kind: 'TEXT', filterable: true, sortable: true, groupable: false, operators: ['contains'] },
          { field: 'createdAt', label: 'permission.applyTime', kind: 'DATETIME', filterable: true, sortable: true, groupable: false, operators: ['between'] },
        ],
        content: [{
          id: '4',
          applicantId: 'u-12345',
          applicantUsername: '12345',
          requestType: 'BUSINESS_UNIT_JOIN',
          targetId: 'bu-hmdc',
          targetName: 'hase-hmdc',
          membershipType: 'MEMBER',
          roleNames: ['HMDC_Approver_Role'],
          reason: 'test',
          status: 'PENDING',
          createdAt: '2026-08-29T00:00:00Z',
        }],
        totalElements: 1,
        page: 0,
        size: 20,
        groups: [],
      },
    } as never)

    wrapper = mount(PermissionRequestSharedList, {
      props: {
        scope: 'APPROVALS_PENDING',
        storageKey: 'test-perm-approvals-pending',
        emptyText: 'empty',
        actionMode: 'approve',
        enabled: true,
      },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(wrapper!.text()).toContain('permission.member')
    expect(wrapper!.text()).not.toContain('permission.leader')
  })

  it('pins the Action column to a pixel width so approve/cancel buttons are not squeezed', async () => {
    wrapper = mount(PermissionRequestSharedList, {
      props: {
        scope: 'APPROVALS_PENDING',
        storageKey: 'test-perm-approve',
        emptyText: 'empty',
        actionMode: 'approve',
        enabled: true,
      },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    const actionCol = wrapper!.findAllComponents({ name: 'ElTableColumn' }).find(
      (col) => col.props('label') === 'common.actions',
    )
    expect(actionCol).toBeTruthy()
    expect(Number(actionCol!.props('width'))).toBe(180)
  }, 15000)
})
