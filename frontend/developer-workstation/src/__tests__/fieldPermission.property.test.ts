import { describe, test, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Property-Based Tests for Task Form Field Permission Values
 * Feature: process-task-form-separation
 *
 * Property 5: Task Form field permission values are valid
 * **Validates: Requirements 3.6, 12.1**
 */

const VALID_PERMISSIONS = ['READONLY', 'EDITABLE'] as const
type FieldPermission = (typeof VALID_PERMISSIONS)[number]

/**
 * Validates that all field permission values are either READONLY or EDITABLE.
 * Returns true if all values are valid.
 */
function validateFieldPermissions(permissions: Record<string, string>): boolean {
  const validSet = new Set<string>(VALID_PERMISSIONS)
  return Object.values(permissions).every(v => validSet.has(v))
}

/**
 * Validates that all field names in permissions are a subset of the form's field names.
 * Returns the list of permission keys not found in formFields.
 */
function validatePermissionFieldSubset(
  permissions: Record<string, string>,
  formFields: string[],
): string[] {
  const fieldSet = new Set(formFields)
  return Object.keys(permissions).filter(k => !fieldSet.has(k))
}

describe('Property 5: Task Form field permission values are valid', () => {
  test('property: permissions with only READONLY/EDITABLE values are valid', () => {
    fc.assert(
      fc.property(
        fc.dictionary(
          fc.string({ minLength: 1, maxLength: 30 }),
          fc.constantFrom(...VALID_PERMISSIONS),
          { minKeys: 0, maxKeys: 20 },
        ),
        (permissions: Record<string, FieldPermission>) => {
          expect(validateFieldPermissions(permissions)).toBe(true)
        },
      ),
      { numRuns: 100 },
    )
  })

  test('property: permissions with invalid values are rejected', () => {
    const invalidPermission = fc.string({ minLength: 1, maxLength: 20 }).filter(
      s => s !== 'READONLY' && s !== 'EDITABLE',
    )

    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 30 }),
        invalidPermission,
        (fieldName: string, badValue: string) => {
          const permissions: Record<string, string> = { [fieldName]: badValue }
          expect(validateFieldPermissions(permissions)).toBe(false)
        },
      ),
      { numRuns: 100 },
    )
  })

  test('property: permission field names must be a subset of form fields', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 30 }), { minLength: 1, maxLength: 15 }),
        (formFields: string[]) => {
          // Build permissions using only form field names
          const permissions: Record<string, string> = {}
          for (const field of formFields) {
            permissions[field] = Math.random() > 0.5 ? 'READONLY' : 'EDITABLE'
          }
          const extraKeys = validatePermissionFieldSubset(permissions, formFields)
          expect(extraKeys).toEqual([])
        },
      ),
      { numRuns: 100 },
    )
  })

  test('property: permission keys not in form fields are detected', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 30 }), { minLength: 1, maxLength: 10 }),
        fc.uniqueArray(fc.string({ minLength: 1, maxLength: 30 }), { minLength: 1, maxLength: 10 }),
        (formFields: string[], extraKeys: string[]) => {
          const formFieldSet = new Set(formFields)
          const trueExtras = extraKeys.filter(k => !formFieldSet.has(k))
          if (trueExtras.length === 0) return // skip if all happen to overlap

          const permissions: Record<string, string> = {}
          for (const field of formFields) {
            permissions[field] = 'EDITABLE'
          }
          for (const extra of trueExtras) {
            permissions[extra] = 'READONLY'
          }

          const detected = validatePermissionFieldSubset(permissions, formFields)
          expect(detected.length).toBe(trueExtras.length)
          for (const k of trueExtras) {
            expect(detected).toContain(k)
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})
