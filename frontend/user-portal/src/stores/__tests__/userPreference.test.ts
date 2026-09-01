import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserPreferenceStore } from '../userPreference'

const { getUserPreference, updateUserPreference } = vi.hoisted(() => ({
  getUserPreference: vi.fn(),
  updateUserPreference: vi.fn(),
}))

vi.mock('@/api/preference', () => ({
  getUserPreference: (...args: unknown[]) => getUserPreference(...args),
  updateUserPreference: (...args: unknown[]) => updateUserPreference(...args),
}))

vi.mock('@/i18n', () => ({
  default: { global: { t: (key: string) => key } },
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn() },
}))

describe('userPreference store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getUserPreference.mockReset()
    updateUserPreference.mockReset()
  })

  it('loads autoClaimOnOpen from GET /preferences', async () => {
    getUserPreference.mockResolvedValue({ data: { autoClaimOnOpen: true, autoPreviewOnOpen: true } })
    const store = useUserPreferenceStore()
    await store.load()
    expect(store.autoClaimOnOpen).toBe(true)
    expect(store.autoPreviewOnOpen).toBe(true)
    expect(store.loaded).toBe(true)
  })

  it('reverts the switch when PUT fails', async () => {
    getUserPreference.mockResolvedValue({
      data: {
        theme: 'light',
        themeColor: '#DB0011',
        fontSize: 'medium',
        layoutDensity: 'normal',
        language: 'en',
        timezone: 'Asia/Shanghai',
        dateFormat: 'YYYY-MM-DD',
        pageSize: 20,
        autoClaimOnOpen: false,
      },
    })
    updateUserPreference.mockRejectedValue(new Error('save failed'))
    const store = useUserPreferenceStore()
    await store.load()
    await store.setAutoClaimOnOpen(true)
    expect(store.autoClaimOnOpen).toBe(false)
    expect(updateUserPreference).toHaveBeenCalledWith(
      expect.objectContaining({
        autoClaimOnOpen: true,
        autoPreviewOnOpen: false,
        theme: 'light',
        language: 'en',
      }),
    )
  })
})
