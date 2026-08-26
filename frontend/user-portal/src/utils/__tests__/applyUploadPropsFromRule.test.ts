import { describe, expect, it } from 'vitest'
import type { FormField } from '@/components/formRendererHelpers'
import {
  applyUploadPropsFromRule,
  collectCannotDownloadFieldKeysFromForms,
} from '../applyUploadPropsFromRule'

function field(): FormField {
  return { key: 'fileupload', label: 'Meeting Doc', type: 'upload' }
}

describe('applyUploadPropsFromRule', () => {
  it('defaults upload url and leaves download allowed when the switch is absent', () => {
    const f = field()
    applyUploadPropsFromRule(f, { type: 'upload', props: {} })
    expect(f.uploadUrl).toBe('/api/v1/upload')
    expect(f.cannotDownload).toBeUndefined()
  })

  it('sets cannotDownload when the designer switch is true', () => {
    const f = field()
    applyUploadPropsFromRule(f, { type: 'upload', props: { cannotDownload: true } })
    expect(f.cannotDownload).toBe(true)
  })

  it('sets cannotDownload from form-create native canNotDownload', () => {
    const f = field()
    applyUploadPropsFromRule(f, { type: 'upload', props: { canNotDownload: true } })
    expect(f.cannotDownload).toBe(true)
  })

  it('inherits cannotDownload from other FU forms for the same field key', () => {
    const f = field()
    const blocked = collectCannotDownloadFieldKeysFromForms([
      {
        data: {
          rule: [{ type: 'upload', field: 'fileupload', props: { cannotDownload: true } }],
        },
      },
    ])
    applyUploadPropsFromRule(f, { type: 'upload', props: {} }, blocked)
    expect(f.cannotDownload).toBe(true)
  })
})

describe('collectCannotDownloadFieldKeysFromForms', () => {
  it('reads Meeting Doc from PROCESS Main even when the REQUEST copy has no switch', () => {
    const keys = collectCannotDownloadFieldKeysFromForms([
      {
        data: JSON.stringify({
          rule: [{ type: 'upload', field: 'fileupload', title: 'Meeting Doc', props: { cannotDownload: true, canNotDownload: false } }],
        }),
      },
      {
        data: JSON.stringify({
          rule: [{ type: 'upload', field: 'fileupload', title: 'Meeting Doc', props: {} }],
        }),
      },
    ])
    expect(keys.has('fileupload')).toBe(true)
  })
})
