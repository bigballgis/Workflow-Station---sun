import { describe, expect, it } from 'vitest'
import en from '@/i18n/locales/en'
import zhCN from '@/i18n/locales/zh-CN'
import zhTW from '@/i18n/locales/zh-TW'
import labels from '@/i18n/application-status-labels.json'

describe('application status painted labels', () => {
  it('locale packs read the shared JSON so Audit search and the grid stay aligned', () => {
    expect(en.application.running).toBe(labels.RUNNING.en)
    expect(en.application.completed).toBe(labels.COMPLETED.en)
    expect(en.application.withdrawn).toBe(labels.WITHDRAWN.en)
    expect(en.application.rejected).toBe(labels.REJECTED.en)
    expect(zhCN.application.running).toBe(labels.RUNNING['zh-CN'])
    expect(zhCN.application.completed).toBe(labels.COMPLETED['zh-CN'])
    expect(zhTW.application.running).toBe(labels.RUNNING['zh-TW'])
    expect(zhTW.application.rejected).toBe(labels.REJECTED['zh-TW'])
  })
})
