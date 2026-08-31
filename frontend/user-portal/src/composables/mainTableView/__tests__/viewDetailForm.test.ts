import { describe, expect, it } from 'vitest'
import { buildViewDetailSubTableBindings, toViewDetailFields } from '../viewDetailForm'

describe('toViewDetailFields', () => {
  it('keeps a nested subTable instead of dropping it', () => {
    const fields = toViewDetailFields(
      [
        { type: 'input', field: 'merchant', title: 'Merchant' },
        { type: 'subTable', _bindingId: 200, title: 'Credit Card Correspondence' },
      ],
      {},
    )
    expect(fields.map(f => f.key)).toEqual(['merchant', '__subTable_200'])
    expect(fields[1]._bindingId).toBe(200)
  })

  it('keeps remaining fields when lookupConfig JSON is malformed', () => {
    const fields = toViewDetailFields(
      [
        { type: 'lookup', field: 'owner', title: 'Owner', props: { lookupConfig: '{not-json' } },
        { type: 'input', field: 'merchant', title: 'Merchant' },
      ],
      {},
    )
    expect(fields.map(f => f.key)).toEqual(['owner', 'merchant'])
    expect(fields[0].type).toBe('lookup')
  })
})

describe('buildViewDetailSubTableBindings', () => {
  it('hydrates nested rows from the view row __subTables__ slice', () => {
    const bindings = buildViewDetailSubTableBindings(
      [
        {
          bindingId: 200,
          bindingType: 'SUB',
          tableName: 'ccc',
          tableDisplayName: 'Credit Card Correspondence',
          tableType: 'SUB',
          fieldDefinitions: [{ fieldName: 'correspondence_type', displayName: 'Type' }],
        },
      ],
      {},
      { __subTables__: { 200: [{ correspondence_type: 'LETTER' }] } },
    )
    expect(bindings).toHaveLength(1)
    expect(bindings[0].bindingId).toBe(200)
    expect(bindings[0].data).toEqual([{ correspondence_type: 'LETTER' }])
    expect(bindings[0].columns.map(c => c.field)).toEqual(['correspondence_type'])
  })
})
