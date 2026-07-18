import { describe, expect, it } from 'vitest'
import {
  clearFcDesignerPreviewComponentEventCache,
  dispatchLookupComponentFieldEvents,
  seedFcDesignerPreviewComponentEventCache,
} from '../formCreateLookupComponentEvents'

describe('formCreateLookupComponentEvents', () => {
  it('dispatches cached change handler when inject.rule omits on (fc preview getJson gap)', () => {
    clearFcDesignerPreviewComponentEventCache()
    const rules = [
      {
        type: 'lookup',
        field: 'test',
        on: {
          change: '$FNX:\nif ($inject.value != null) { $inject.api.setValue("testvalue", "successful") }',
        },
      },
      { type: 'input', field: 'testvalue' },
    ]
    seedFcDesignerPreviewComponentEventCache(rules)

    const formData: Record<string, unknown> = { test: null, testvalue: '' }
    const fcApi = {
      get form() {
        return formData
      },
      getValue(field: string) {
        return formData[field]
      },
      setValue(fieldOrData: string | Record<string, unknown>, value?: unknown) {
        if (typeof fieldOrData === 'object' && fieldOrData !== null && !Array.isArray(fieldOrData)) {
          Object.assign(formData, fieldOrData)
          return
        }
        if (typeof fieldOrData === 'string') {
          formData[fieldOrData] = value
        }
      },
    }

    dispatchLookupComponentFieldEvents(
      {
        field: 'test',
        api: fcApi,
        rule: { type: 'lookup', field: 'test' },
      },
      { id: 'row-1' },
    )

    expect(formData.testvalue).toBe('successful')
  })
})
