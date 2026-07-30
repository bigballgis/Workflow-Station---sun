import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useSubTableDialogComponentEvents } from '../useSubTableDialogComponentEvents'

describe('useSubTableDialogComponentEvents (DW)', () => {
  it('runs select change when column keeps sourceRule (row-dialog mapping must not strip it)', () => {
    const formData = ref<Record<string, unknown>>({ case_type: null, card_number: '' })
    const columns = [
      {
        field: 'case_type',
        label: 'Case Type',
        type: 'select',
        sourceRule: {
          type: 'select',
          field: 'case_type',
          _on: {
            blur: '$FNX:\nvar v = $inject.value\nif (v === 1 || v === \'1\') { $inject.api.setValue(\'card_number\', \'111\') }',
          },
        },
      },
      { field: 'card_number', label: 'Card Number', type: 'text' },
    ]
    const { onDialogFieldChange } = useSubTableDialogComponentEvents(formData, () => columns)
    onDialogFieldChange('case_type', 1)
    expect(formData.value.card_number).toBe('111')
  })

  it('hides sibling lookup via api.hidden on select change in Preview Add dialog', () => {
    const formData = ref<Record<string, unknown>>({ select: 'Option02', lookup: null })
    const columns = [
      {
        field: 'select',
        label: 'select',
        type: 'select',
        sourceRule: {
          type: 'select',
          field: 'select',
          _on: {
            change: '$FNX:\napi.hidden(true, "lookup")',
          },
        },
      },
      { field: 'lookup', label: 'lookup', type: 'lookup' },
    ]
    const { onDialogFieldChange, isDialogFieldVisible } = useSubTableDialogComponentEvents(
      formData,
      () => columns,
    )
    onDialogFieldChange('select', 'Option02')
    expect(isDialogFieldVisible('lookup')).toBe(false)
  })
})
