import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailLinkBindings } from '../useApplicationDetailLinkBindings'

/**
 * After extract keeps Credit Card Correspondence on the ACQ Transaction form,
 * {@code stripLinkOnlySubTableFieldsFromBindings} must not delete it again just
 * because the nested table is FORM_ONLY / also a Link Form target.
 */
describe('stripLinkOnlySubTableFieldsFromBindings — nested canvas subTable', () => {
  it('keeps a FORM_ONLY nested subTable placed on the opened sub-form', () => {
    const { stripLinkOnlySubTableFieldsFromBindings } = createApplicationDetailLinkBindings({
      formFields: ref([]),
      formTabs: ref([]),
      subTableBindings: ref([]),
      mainFormNativeSubTableBindingIds: ref([]),
    } as never)

    const bindings = [
      {
        bindingId: 100,
        formFields: [
          { key: 'dispute_amount', label: 'Dispute Amount', type: 'text' },
          { key: '__subTable_200', label: '', type: 'subTable', _bindingId: 200 },
        ],
      },
      {
        bindingId: 200,
        subMode: 'FORM_ONLY',
        formFields: [],
      },
    ]
    const subForms = {
      100: {
        rule: [
          { type: 'input', field: 'dispute_amount' },
          { type: 'subTable', _bindingId: 200 },
        ],
      },
    }

    stripLinkOnlySubTableFieldsFromBindings(bindings, subForms)

    expect(bindings[0].formFields.map(f => f.key)).toEqual([
      'dispute_amount',
      '__subTable_200',
    ])
  })
})
