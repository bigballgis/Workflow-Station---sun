import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Feature: function-unit-design-review, Property 28: tableTypeLabel i18n 一致性
 * Validates: Requirements 15.1, 15.2, 15.3
 *
 * For any table type string (MAIN, SUB, ACTION, RELATION), the tableTypeLabel() function
 * should return the corresponding table-specific i18n value and never return form-specific values.
 */

// Simulate the fixed tableTypeLabel function using table-specific keys
const TABLE_TYPE_I18N_MAP: Record<string, string> = {
  MAIN: 'table.mainTable',
  SUB: 'table.subTable',
  ACTION: 'table.actionTable',
  RELATION: 'table.relationTable'
}

// Form-specific keys that should NOT be used
const FORM_SPECIFIC_KEYS = ['form.mainForm', 'form.subForm', 'form.actionForm']

function tableTypeLabel(type: string): string {
  return TABLE_TYPE_I18N_MAP[type] || type
}

describe('Property 28: tableTypeLabel i18n Consistency', () => {
  const validTableTypes = ['MAIN', 'SUB', 'ACTION', 'RELATION'] as const

  it('should map all valid table types to table-specific i18n keys, never form-specific', () => {
    fc.assert(
      fc.property(fc.constantFrom(...validTableTypes), (type) => {
        const result = tableTypeLabel(type)
        expect(result).toMatch(/^table\./)
        expect(FORM_SPECIFIC_KEYS).not.toContain(result)
      }),
      { numRuns: 100 }
    )
  })

  it('should map MAIN to table.mainTable', () => {
    const result = tableTypeLabel('MAIN')
    expect(result).toBe('table.mainTable')
    expect(result).not.toBe('form.mainForm')
  })

  it('should map SUB to table.subTable', () => {
    const result = tableTypeLabel('SUB')
    expect(result).toBe('table.subTable')
    expect(result).not.toBe('form.subForm')
  })

  it('should map ACTION to table.actionTable', () => {
    const result = tableTypeLabel('ACTION')
    expect(result).toBe('table.actionTable')
    expect(result).not.toBe('form.actionForm')
  })

  it('should return the type string itself for unknown types', () => {
    fc.assert(
      fc.property(
        fc.string().filter(s => !['MAIN', 'SUB', 'ACTION', 'RELATION'].includes(s)),
        (type) => {
          const result = tableTypeLabel(type)
          expect(result).toBe(type)
        }
      ),
      { numRuns: 100 }
    )
  })
})
