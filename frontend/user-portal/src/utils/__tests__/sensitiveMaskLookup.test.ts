import { describe, expect, it } from 'vitest'
import {
  buildSensitiveMaskLookup,
  collectMasksFromFormCreateRules,
  emptySensitiveMaskLookup,
  parseFormConfigJsonsProcessFirst,
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

  it('parses Portal FormContentDTO.data and keeps PROCESS first for Change History', () => {
    const configs = parseFormConfigJsonsProcessFirst([
      {
        type: 'FORM',
        formType: 'TASK',
        data: JSON.stringify({
          rule: [{
            type: 'input',
            field: 'card',
            props: {
              sensitiveMask: {
                enabled: true,
                preset: 'custom',
                keepPrefix: 2,
                keepSuffix: 4,
              },
            },
          }],
        }),
      },
      {
        type: 'FORM',
        formType: 'PROCESS',
        data: JSON.stringify({
          rule: [{
            type: 'input',
            field: 'card',
            props: {
              sensitiveMask: {
                enabled: true,
                preset: 'ranges',
                maskRanges: [{ start: 2, end: 8 }],
              },
            },
          }],
        }),
      },
    ])
    const lookup = buildSensitiveMaskLookup({ formConfigJsons: configs })
    expect(lookup.get('card')?.preset).toBe('ranges')
  })
})
