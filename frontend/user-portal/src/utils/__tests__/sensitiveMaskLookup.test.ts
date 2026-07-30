import { describe, expect, it } from 'vitest'
import {
  buildSensitiveMaskLookup,
  collectMasksFromFormCreateRules,
  emptySensitiveMaskLookup,
} from '../sensitiveMaskLookup'

describe('sensitiveMaskLookup', () => {
  it('collects Input sensitiveMask from form-create rules', () => {
    const lookup = emptySensitiveMaskLookup()
    collectMasksFromFormCreateRules(lookup, [
      {
        type: 'input',
        field: 'card_number',
        props: {
          sensitiveMask: { enabled: true, preset: 'last4' },
        },
      },
      {
        type: 'input',
        field: 'notes',
        props: { type: 'textarea', sensitiveMask: { enabled: true, preset: 'all' } },
      },
      {
        type: 'input',
        field: 'pwd',
        props: { type: 'password', sensitiveMask: { enabled: true, preset: 'all' } },
      },
    ])
    expect(lookup.has('card_number')).toBe(true)
    expect(lookup.has('notes')).toBe(false)
    expect(lookup.has('pwd')).toBe(false)
  })

  it('buildSensitiveMaskLookup merges form fields and columns', () => {
    const lookup = buildSensitiveMaskLookup({
      formFields: [
        {
          key: 'main_card',
          label: 'Card',
          type: 'text',
          sensitiveMask: { enabled: true, preset: 'last4' },
        },
      ],
      subTableBindings: [
        {
          columns: [
            {
              field: 'sub_card',
              type: 'text',
              props: { sensitiveMask: { enabled: true, preset: 'first4Last4' } },
            },
          ],
        },
      ],
    })
    expect(lookup.get('main_card')?.preset).toBe('last4')
    expect(lookup.get('sub_card')?.preset).toBe('first4Last4')
  })
})
