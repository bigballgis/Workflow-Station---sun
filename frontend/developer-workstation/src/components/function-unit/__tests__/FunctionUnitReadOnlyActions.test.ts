import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import FunctionUnitCard from '../FunctionUnitCard.vue'
import { USER_KEY } from '@/api/auth'
import type { FunctionUnitResponse } from '@/api/functionUnit'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

function item(canModify?: boolean): FunctionUnitResponse {
  return {
    id: 1,
    name: 'Invoice',
    status: 'DRAFT',
    createdAt: '2026-01-01',
    tableCount: 0,
    formCount: 0,
    actionCount: 0,
    hasProcess: false,
    canModify,
  }
}

describe('FunctionUnitReadOnlyActions', () => {
  const storage = new Map<string, string>()

  beforeEach(() => {
    storage.clear()
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key: string) => storage.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => {
        storage.set(key, value)
      }),
      removeItem: vi.fn((key: string) => {
        storage.delete(key)
      }),
    })
    storage.set(USER_KEY, JSON.stringify({
      userId: 'u1',
      username: 'dev',
      displayName: 'Dev',
      email: 'dev@example.com',
      roles: ['DEVELOPER'],
      permissions: [],
      language: 'en',
      hasAvatar: false,
    }))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('hides mutation actions when canModify is missing', () => {
    const wrapper = mount(FunctionUnitCard, {
      props: { item: item(), tags: [] },
      global: { stubs: ['el-button', 'el-icon', 'el-tag', 'IconPreview'] },
    })
    expect(wrapper.find('.card-actions').exists()).toBe(false)
  })

  it('hides mutation actions when canModify is false', () => {
    const wrapper = mount(FunctionUnitCard, {
      props: { item: item(false), tags: [] },
      global: { stubs: ['el-button', 'el-icon', 'el-tag', 'IconPreview'] },
    })
    expect(wrapper.find('.card-actions').exists()).toBe(false)
  })

  it('shows mutation actions when canModify is true', () => {
    const wrapper = mount(FunctionUnitCard, {
      props: { item: item(true), tags: [] },
      global: { stubs: ['el-button', 'el-icon', 'el-tag', 'IconPreview'] },
    })
    expect(wrapper.find('.card-actions').exists()).toBe(true)
  })
})
