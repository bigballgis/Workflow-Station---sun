import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailFormSchema } from '../useApplicationDetailFormSchema'

/**
 * Audit / Views MAIN → My Request → Link Form Details uses {@code resolveSubFormDesign}
 * for the opened row's form. To Do's extractor has no skipSubTable gate, so a nested
 * {@code subTable} (Credit Card Correspondence on ACQ Transaction) survives even when
 * form-create wraps it in {@code subForm}. My Request used to drop that widget.
 */
function schema() {
  return createApplicationDetailFormSchema({
    lookupDbConfigs: ref({}),
    relationViewConfigs: ref({}),
    formFields: ref([]),
    formTabs: ref([]),
    formFieldsAfterTabs: ref([]),
    formFormOptions: ref({}),
    cachedContentForms: [],
  } as never)
}

const ACQ_SUBFORM_RULE = [
  { type: 'input', field: 'dispute_amount', title: 'Dispute Amount' },
  {
    type: 'elCard',
    title: 'Financial adjustment',
    children: [
      { type: 'select', field: 'merchant_credit', title: 'Merchant Credit' },
    ],
  },
  {
    type: 'subForm',
    children: [
      { type: 'subTable', _bindingId: 200, title: 'Credit Card Correspondence' },
    ],
  },
  { type: 'recordNote', props: { scope: 'RECORD' } },
]

describe('resolveSubFormDesign — nested subTable on a Link Form target', () => {
  it('keeps a nested subTable inside a subForm wrapper (To Do parity)', () => {
    const { resolveSubFormDesign } = schema()
    const { formFields } = resolveSubFormDesign(
      { bindingId: 100 },
      { 100: { rule: ACQ_SUBFORM_RULE } },
    )
    const nested = formFields.find(f => f.type === 'subTable' && f._bindingId === 200)
    expect(nested).toBeDefined()
    expect(nested!.key).toBe('__subTable_200')
  })

  it('still extracts ordinary fields around the nested table', () => {
    const { resolveSubFormDesign } = schema()
    const { formFields } = resolveSubFormDesign(
      { bindingId: 100 },
      { 100: { rule: ACQ_SUBFORM_RULE } },
    )
    expect(formFields.some(f => f.key === 'dispute_amount')).toBe(true)
    expect(formFields.some(f => f.type === 'recordNote')).toBe(true)
  })

  it('does not promote that nested subTable when extracting the MAIN canvas', () => {
    const { extractFieldsRecursive } = schema()
    const mainRule = [
      { type: 'input', field: 'case_number', title: 'Case Number' },
      { type: 'subTable', _bindingId: 100, title: 'ACQ Transaction' },
      {
        type: 'subForm',
        children: [
          { type: 'subTable', _bindingId: 200, title: 'Credit Card Correspondence' },
        ],
      },
    ]
    const fields = extractFieldsRecursive(mainRule as never)
    expect(fields.some(f => f._bindingId === 100 && f.type === 'subTable')).toBe(true)
    expect(fields.some(f => f._bindingId === 200)).toBe(false)
  })
})
