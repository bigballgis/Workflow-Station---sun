import { describe, test, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Property-Based Tests for Field Name Validation
 * Feature: process-task-form-separation
 *
 * Property 3: Form field names must reference Data_Table columns
 * **Validates: Requirements 2.5, 3.3, 3.4**
 */

/**
 * Validates that a set of field names are all present in the Data_Table columns.
 * Returns the list of invalid field names (not found in columns).
 */
function validateFieldNames(fieldNames: string[], dataTableColumns: string[]): string[] {
  const columnSet = new Set(dataTableColumns)
  return fieldNames.filter(name => !columnSet.has(name))
}

describe('Property 3: Form field names must reference Data_Table columns', () => {
  test('property: field names that exist in Data_Table columns are accepted', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 50 }), { minLength: 1, maxLength: 20 }),
        (columns: string[]) => {
          // Pick a subset of columns as field names — all should be valid
          const fieldNames = columns.slice(0, Math.max(1, Math.floor(columns.length / 2)))
          const invalid = validateFieldNames(fieldNames, columns)
          expect(invalid).toEqual([])
        },
      ),
      { numRuns: 100 },
    )
  })

  test('property: field names not in Data_Table columns are rejected', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 50 }), { minLength: 1, maxLength: 10 }),
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 50 }), { minLength: 1, maxLength: 10 }),
        (columns: string[], extraNames: string[]) => {
          // Filter extraNames to only those NOT in columns
          const columnSet = new Set(columns)
          const invalidFieldNames = extraNames.filter(n => !columnSet.has(n))
          if (invalidFieldNames.length === 0) return // skip if all happen to be in columns

          const result = validateFieldNames(invalidFieldNames, columns)
          expect(result.length).toBe(invalidFieldNames.length)
          for (const name of invalidFieldNames) {
            expect(result).toContain(name)
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  test('property: mixed valid and invalid field names — only invalid ones rejected', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 50 }), { minLength: 2, maxLength: 15 }),
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 50 }), { minLength: 1, maxLength: 10 }),
        (columns: string[], extraNames: string[]) => {
          const columnSet = new Set(columns)
          const validSubset = columns.slice(0, Math.max(1, Math.floor(columns.length / 2)))
          const invalidSubset = extraNames.filter(n => !columnSet.has(n))

          const allFieldNames = [...validSubset, ...invalidSubset]
          const result = validateFieldNames(allFieldNames, columns)

          // Only the invalid ones should be returned
          expect(result.length).toBe(invalidSubset.length)
          for (const name of validSubset) {
            expect(result).not.toContain(name)
          }
          for (const name of invalidSubset) {
            expect(result).toContain(name)
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  test('property: empty field names list always passes validation', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 50 }), { minLength: 0, maxLength: 10 }),
        (columns: string[]) => {
          const result = validateFieldNames([], columns)
          expect(result).toEqual([])
        },
      ),
      { numRuns: 100 },
    )
  })
})
