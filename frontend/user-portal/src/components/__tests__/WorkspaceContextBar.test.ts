import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import WorkspaceContextBar from '../WorkspaceContextBar.vue'

const { getUser, listWorkspaceContexts, saveTokens, saveUser, switchWorkspace } = vi.hoisted(() => ({
  getUser: vi.fn(),
  listWorkspaceContexts: vi.fn(),
  saveTokens: vi.fn(),
  saveUser: vi.fn(),
  switchWorkspace: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  USER_ID_KEY: 'ws_up_user_id',
  getUser,
  listWorkspaceContexts,
  saveTokens,
  saveUser,
  switchWorkspace,
}))
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

describe('WorkspaceContextBar', () => {
  const storage = new Map<string, string>()
  const assign = vi.fn()
  const currentUser = {
    userId: 'user-1',
    username: 'developer',
    displayName: 'Developer',
    email: 'developer@example.test',
    roles: [],
    permissions: [],
    language: 'en',
    activeBusinessUnitId: 'bu-alpha',
    activeRoleId: 'role-alpha',
    workspaceSwitcherVisible: true,
    hasAvatar: false,
  }

  beforeEach(() => {
    storage.clear()
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key: string) => storage.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => storage.set(key, value)),
      removeItem: vi.fn((key: string) => storage.delete(key)),
    })
    vi.stubGlobal('window', { location: { assign } })
    getUser.mockReturnValue(currentUser)
    listWorkspaceContexts.mockResolvedValue([])
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  it('returns to the dashboard after a successful workspace switch', async () => {
    const nextWorkspace = {
      businessUnitId: 'bu-beta',
      roleId: 'role-beta',
      businessUnitName: 'Beta',
      roleName: 'Reviewer',
    }
    const switchedUser = {
      ...currentUser,
      activeBusinessUnitId: nextWorkspace.businessUnitId,
      activeRoleId: nextWorkspace.roleId,
    }
    switchWorkspace.mockResolvedValue({
      accessToken: 'access',
      refreshToken: 'refresh',
      user: switchedUser,
    })
    const wrapper = mount(WorkspaceContextBar, {
      global: {
        stubs: ['el-button', 'el-dropdown', 'el-dropdown-item', 'el-dropdown-menu', 'el-icon'],
      },
    })
    await flushPromises()

    await (wrapper.vm as any).onSwitch(nextWorkspace)

    expect(switchWorkspace).toHaveBeenCalledWith('bu-beta', 'role-beta')
    expect(saveUser).toHaveBeenCalledWith(switchedUser)
    expect(storage.get('ws_up_user_id')).toBe('user-1')
    expect(assign).toHaveBeenCalledWith(`${import.meta.env.BASE_URL}dashboard`)
  })
})
