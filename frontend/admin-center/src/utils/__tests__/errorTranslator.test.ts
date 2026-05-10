import { describe, it, expect } from 'vitest'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'

describe('errorTranslator', () => {
  it('returns i18n key for every AppErrorCode', () => {
    for (const code of Object.values(AppErrorCode)) {
      const key = errorTranslator(code)
      expect(key).toBeTruthy()
      expect(key).toMatch(/^errors\./)
    }
  })

  it('returns "errors.unknown" for unknown code', () => {
    expect(errorTranslator('NONEXISTENT' as any)).toBe('errors.unknown')
  })

  it('bi.dashboard codes map correctly', () => {
    expect(errorTranslator(AppErrorCode.BI_DASHBOARD_QUERY_FAILED)).toBe('errors.biDashboardQueryFailed')
    expect(errorTranslator(AppErrorCode.BI_DASHBOARD_SYNC_FAILED)).toBe('errors.biDashboardSyncFailed')
  })

  it('functionUnit codes map correctly', () => {
    expect(errorTranslator(AppErrorCode.FUNCTION_UNIT_DEPLOY_FAILED)).toBe('errors.functionUnitDeployFailed')
    expect(errorTranslator(AppErrorCode.FUNCTION_UNIT_LOAD_FAILED)).toBe('errors.functionUnitLoadFailed')
  })

  it('common codes map correctly', () => {
    expect(errorTranslator(AppErrorCode.COMMON_FAILED)).toBe('errors.commonFailed')
    expect(errorTranslator(AppErrorCode.COMMON_QUERY_FAILED)).toBe('errors.commonQueryFailed')
  })
})
