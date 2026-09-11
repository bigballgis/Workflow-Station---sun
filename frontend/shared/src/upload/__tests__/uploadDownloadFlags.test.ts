import { describe, expect, it } from 'vitest'
import { isCannotDownload, uploadPropsBlockDownload } from '../uploadDownloadFlags'

describe('isCannotDownload', () => {
  it('is true only for explicit on values', () => {
    expect(isCannotDownload(true)).toBe(true)
    expect(isCannotDownload('true')).toBe(true)
    expect(isCannotDownload(1)).toBe(true)
    expect(isCannotDownload(false)).toBe(false)
    expect(isCannotDownload('false')).toBe(false)
    expect(isCannotDownload(0)).toBe(false)
    expect(isCannotDownload(undefined)).toBe(false)
  })
})

describe('uploadPropsBlockDownload', () => {
  it('reads both designer and form-create spellings', () => {
    expect(uploadPropsBlockDownload({ cannotDownload: true })).toBe(true)
    expect(uploadPropsBlockDownload({ canNotDownload: 'true' })).toBe(true)
    expect(uploadPropsBlockDownload({ cannotDownload: false })).toBe(false)
    expect(uploadPropsBlockDownload(undefined)).toBe(false)
  })
})
