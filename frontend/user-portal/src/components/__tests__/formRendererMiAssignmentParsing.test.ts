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

  /**
   * The owned fields belong to the marker, not beside it: a hidden marker must be
   * able to take them with it. Hoisting them left an undesigned Assignee row in
   * the dialog while the block itself correctly disappeared.
   */
  it('nests the container children under the marker', () => {
    const fields = extractFieldsRecursive([
      {
        type: 'miAssignment',
        name: 'assignment-marker',
        input: false,
        children: [{ type: 'input', field: 'assignee' }],
      },
    ], item => ({
      key: String(item.field),
      label: String(item.field),
      type: 'text',
    }))

    expect(fields.map(f => f.key)).toEqual(['assignment-marker'])
    expect((fields[0].children || []).map(c => c.key)).toEqual(['assignee'])
  })
})
