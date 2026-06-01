import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { materializePreviewComponentEvents } from '../formCreatePreviewEvents'

describe('formCreatePreviewEvents', () => {
  it('binds blur handler so preview can toggle legal_hold on exact match', () => {
    const previewData = ref<Record<string, unknown>>({
      case_number: '',
      legal_hold: false,
    })
    const rules = [
      {
        type: 'input',
        field: 'case_number',
        on: {
          blur: '$FNX:\nif ($inject.value === "abc") { $inject.api.setValue("legal_hold", true) } else { $inject.api.setValue("legal_hold", false) }',
        },
      },
      { type: 'switch', field: 'legal_hold' },
    ]
    materializePreviewComponentEvents(rules, previewData)
    previewData.value.case_number = 'abc'
    const on = rules[0].on as Record<string, () => void>
    expect(typeof on.blur).toBe('function')
    on.blur()
    expect(previewData.value.legal_hold).toBe(true)
    previewData.value.case_number = 'abcd'
    on.blur()
    expect(previewData.value.legal_hold).toBe(false)
  })
})
