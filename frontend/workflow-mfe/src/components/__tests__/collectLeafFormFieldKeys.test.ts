import { describe, expect, it } from 'vitest'
import { collectLeafFormFieldKeys, type FormField } from '@/components/formRendererHelpers'

describe('collectLeafFormFieldKeys', () => {
  it('recurses elCard children so Case Info scalars are included', () => {
    const fields: FormField[] = [
      {
        key: '__layout_card_0',
        label: 'Case Info',
        type: 'card',
        children: [
          { key: 'case_number', label: 'Case Number', type: 'text' },
          { key: 'legal_hold', label: 'Legal Hold', type: 'switch' },
        ],
      } as FormField,
    ]
    expect(collectLeafFormFieldKeys(fields).sort()).toEqual(['case_number', 'legal_hold'])
  })

  it('skips subTable placeholders and layout keys', () => {
    const fields: FormField[] = [
      { key: '__subTable_271', label: '', type: 'subTable', _bindingId: 271 },
      { key: 'notes', label: 'Notes', type: 'textarea' },
    ]
    expect(collectLeafFormFieldKeys(fields)).toEqual(['notes'])
  })
})
