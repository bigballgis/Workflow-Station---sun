import { describe, expect, it } from 'vitest'
import { extractFieldsRecursive } from '../formRendererHelpers/formRendererRuleParsing'

describe('formRenderer miAssignment parsing', () => {
  it('preserves miAssignment as a non-data layout marker', () => {
    const fields = extractFieldsRecursive([
      { type: 'input', field: 'before' },
      { type: 'miAssignment', name: 'assignment-marker', input: false },
      { type: 'input', field: 'after' },
    ], item => ({
      key: String(item.field),
      label: String(item.field),
      type: 'text',
    }))

    expect(fields.map(field => [field.key, field.type])).toEqual([
      ['before', 'text'],
      ['assignment-marker', 'miAssignment'],
      ['after', 'text'],
    ])
    expect(fields[1]).not.toHaveProperty('defaultValue')
  })
})
