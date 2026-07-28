import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import DevGroupContextBar from '../DevGroupContextBar.vue'
import { clearActiveGroup, getActiveGroupRaw } from '@/utils/devGroupContext'
const { getMyDevGroups } = vi.hoisted(() => ({ getMyDevGroups: vi.fn() }))
vi.mock('@/api/functionUnit', () => ({
  functionUnitApi: { getMyDevGroups },
}))
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))
describe('DevGroupContextBar', () => {
  const storage = new Map<string, string>()
  beforeEach(() => {
    storage.clear()
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key: string) => storage.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => storage.set(key, value)),
      removeItem: vi.fn((key: string) => storage.delete(key)),
    })
  })
  afterEach(() => {
    clearActiveGroup()
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })
  it('emits ready after persisting a single available team', async () => {
    getMyDevGroups.mockResolvedValue({
      data: {
        groups: [{ id: 'vg-department-managers', name: 'Department managers' }],
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
})