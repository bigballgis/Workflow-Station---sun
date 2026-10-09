import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import DevGroupContextBar from '../DevGroupContextBar.vue'
import { clearActiveGroup, getActiveGroupRaw } from '@/utils/devGroupContext'
const { getMyDevGroups } = vi.hoisted(() => ({ getMyDevGroups: vi.fn() }))
const { confirmUnsavedDesignerBeforeReload } = vi.hoisted(() => ({ confirmUnsavedDesignerBeforeReload: vi.fn() }))
vi.mock('@/api/functionUnit', () => ({
  functionUnitApi: { getMyDevGroups },
}))
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('@/composables/useUnsavedDesignerNavigation', () => ({
  confirmUnsavedDesignerBeforeReload,
}))
describe('DevGroupContextBar', () => {
  const storage = new Map<string, string>()
  const assign = vi.fn()
  beforeEach(() => {
    storage.clear()
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key: string) => storage.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => storage.set(key, value)),
      removeItem: vi.fn((key: string) => storage.delete(key)),
    })
    vi.stubGlobal('window', { location: { assign } })
    confirmUnsavedDesignerBeforeReload.mockResolvedValue(true)
  })
  afterEach(() => {
    clearActiveGroup()
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })
  it('emits ready after persisting a single available team', async () => {
    getMyDevGroups.mockResolvedValue({
      data: {
        groups: [{ id: 'vg-department-managers', name: 'Department managers', status: 'ACTIVE' }],
        canSeeAllGroups: false,
        publicGroupId: 'vg-dev-public',
      },
    })
    const wrapper = mount(DevGroupContextBar, {
      global: {
        stubs: ['el-button', 'el-dialog', 'el-dropdown', 'el-dropdown-item', 'el-dropdown-menu', 'el-icon', 'el-radio', 'el-radio-group'],
      },
    })
    await flushPromises()
    expect(getActiveGroupRaw()).toBe('vg-department-managers')
    expect(wrapper.emitted('ready')).toHaveLength(1)
  })

  it('does not select an inactive team as the current workspace', async () => {
    getMyDevGroups.mockResolvedValue({
      data: {
        groups: [
          { id: 'vg-inactive', name: 'Inactive team', status: 'INACTIVE' },
          { id: 'vg-active', name: 'Active team', status: 'ACTIVE' },
        ],
        canSeeAllGroups: false,
        publicGroupId: 'vg-dev-public',
      },
    })
    mount(DevGroupContextBar, {
      global: {
        stubs: ['el-button', 'el-dialog', 'el-dropdown', 'el-dropdown-item', 'el-dropdown-menu', 'el-icon', 'el-radio', 'el-radio-group'],
      },
    })
    await flushPromises()
    expect(getActiveGroupRaw()).toBe('vg-active')
  })

  it('returns to the function unit list after switching workspaces', async () => {
    getMyDevGroups.mockResolvedValue({
      data: {
        groups: [
          { id: 'vg-alpha', name: 'Alpha', status: 'ACTIVE' },
          { id: 'vg-beta', name: 'Beta', status: 'ACTIVE' },
        ],
        canSeeAllGroups: false,
        publicGroupId: null,
      },
    })
    storage.set('ws_dw_active_group', 'vg-alpha')
    const wrapper = mount(DevGroupContextBar, {
      global: {
        stubs: ['el-button', 'el-dialog', 'el-dropdown', 'el-dropdown-item', 'el-dropdown-menu', 'el-icon', 'el-radio', 'el-radio-group'],
      },
    })
    await flushPromises()

    await (wrapper.vm as any).onSwitch('vg-beta')

    expect(getActiveGroupRaw()).toBe('vg-beta')
    expect(assign).toHaveBeenCalledWith(`${import.meta.env.BASE_URL}function-units`)
  })

  it('does not switch workspaces when the unsaved-designer confirmation is declined', async () => {
    getMyDevGroups.mockResolvedValue({
      data: {
        groups: [
          { id: 'vg-alpha', name: 'Alpha', status: 'ACTIVE' },
          { id: 'vg-beta', name: 'Beta', status: 'ACTIVE' },
        ],
        canSeeAllGroups: false,
        publicGroupId: null,
      },
    })
    storage.set('ws_dw_active_group', 'vg-alpha')
    confirmUnsavedDesignerBeforeReload.mockResolvedValue(false)
    const wrapper = mount(DevGroupContextBar, {
      global: {
        stubs: ['el-button', 'el-dialog', 'el-dropdown', 'el-dropdown-item', 'el-dropdown-menu', 'el-icon', 'el-radio', 'el-radio-group'],
      },
    })
    await flushPromises()

    await (wrapper.vm as any).onSwitch('vg-beta')

    expect(getActiveGroupRaw()).toBe('vg-alpha')
    expect(assign).not.toHaveBeenCalled()
  })
})
