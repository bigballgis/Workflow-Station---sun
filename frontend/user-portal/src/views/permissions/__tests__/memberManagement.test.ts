import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import MemberManagement from '@/views/permissions/member-management.vue'
import { permissionApi } from '@/api/permission'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/permission', () => ({
  permissionApi: {
    getBusinessUnits: vi.fn(),
    getBusinessUnitMembers: vi.fn(),
    removeBusinessUnitRole: vi.fn(),
  },
}))

let wrapper: VueWrapper | null = null

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  vi.mocked(permissionApi.getBusinessUnits).mockResolvedValue({ success: true, data: [{ id: 'bu-1', name: 'Finance' }] } as never)
  vi.mocked(permissionApi.getBusinessUnitMembers).mockResolvedValue({
    success: true,
    data: [{ userId: 'u-ann', username: 'ann', fullName: 'Ann Lee', roles: [{ id: 'r-clerk', code: 'CLERK', name: 'Clerk' }] }],
  } as never)
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

/** Member Management: business-unit members with their roles; virtual groups are not in the portal. */
describe('member management page', () => {
  it('offers business-unit members only, with no virtual-group tab', async () => {
    wrapper = mount(MemberManagement, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('memberManagement.businessUnitMembers')
    expect(wrapper.text()).not.toContain('memberManagement.virtualGroupMembers')
    expect(permissionApi.getBusinessUnits).toHaveBeenCalledTimes(1)
  })

  it('lists a unit\'s members with the roles they hold there', async () => {
    wrapper = mount(MemberManagement, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const setup = (wrapper.vm as unknown as { $: { setupState: Record<string, unknown> } }).$.setupState
    setup.selectedBusinessUnit = 'bu-1'
    await (setup.loadBusinessUnitMembers as () => Promise<void>)()
    await flushPromises()

    expect(permissionApi.getBusinessUnitMembers).toHaveBeenCalledWith('bu-1')
    expect(wrapper.text()).toContain('Ann Lee')
    expect(wrapper.text()).toContain('Clerk')
    // The role tag carries its own remove button, on the role's line.
    expect(wrapper.find('.member-role-tag .el-tag__close').exists()).toBe(true)
  })
})
