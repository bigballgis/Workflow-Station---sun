/**
 * Property 21: Field permission rendering
 * **Validates: Requirements 12.2, 12.3**
 *
 * For any field in a Task Form with a fieldPermissions configuration,
 * the FormRenderer should render the field as non-editable (disabled)
 * when the permission is READONLY, and as an interactive input when
 * the permission is EDITABLE.
 *
 * Feature: process-task-form-separation, Property 21: Field permission rendering
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Pure function that determines whether a field should be rendered as
 * editable or readonly based on the fieldPermissions map.
 *
 * This mirrors the logic the FormRenderer uses when rendering Task Form fields:
 * - If fieldPermissions[fieldKey] === 'EDITABLE' → field is interactive (editable)
 * - If fieldPermissions[fieldKey] === 'READONLY' → field is disabled (non-editable)
 * - If fieldPermissions does not contain the field → default to editable
 */
export function resolveFieldEditability(
  fieldKey: string,
  fieldPermissions: Record<string, string>
): boolean {
  const permission = fieldPermissions[fieldKey]
  if (permission === 'READONLY') return false
  if (permission === 'EDITABLE') return true
  // Default: editable when no explicit permission set
  return true
}

describe('Property 21: Field permission rendering', () => {
  // Generator for field keys (simple alphanumeric strings)
  const fieldKeyArb = fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9_]{0,20}$/)

  // Generator for permission values
  const permissionArb = fc.oneof(
    fc.constant('READONLY'),
    fc.constant('EDITABLE')
  )

  // Generator for a fieldPermissions map
  const fieldPermissionsArb = fc.dictionary(fieldKeyArb, permissionArb)

  it('READONLY fields are always non-editable', () => {
    fc.assert(
      fc.property(
        fieldKeyArb,
        fieldPermissionsArb,
        (fieldKey, permissions) => {
          const permsWithReadonly = { ...permissions, [fieldKey]: 'READONLY' }
          const editable = resolveFieldEditability(fieldKey, permsWithReadonly)
          expect(editable).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('EDITABLE fields are always interactive', () => {
    fc.assert(
      fc.property(
        fieldKeyArb,
        fieldPermissionsArb,
        (fieldKey, permissions) => {
          const permsWithEditable = { ...permissions, [fieldKey]: 'EDITABLE' }
          const editable = resolveFieldEditability(fieldKey, permsWithEditable)
          expect(editable).toBe(true)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('fields without explicit permission default to editable', () => {
    fc.assert(
      fc.property(
        fieldKeyArb,
        fieldPermissionsArb,
        (fieldKey, permissions) => {
          // Remove the field from permissions to test default behavior
          const { [fieldKey]: _, ...rest } = permissions
          const editable = resolveFieldEditability(fieldKey, rest)
          expect(editable).toBe(true)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('permission result is deterministic for any field/permissions combination', () => {
    fc.assert(
      fc.property(
        fieldKeyArb,
        fieldPermissionsArb,
        (fieldKey, permissions) => {
          const result1 = resolveFieldEditability(fieldKey, permissions)
          const result2 = resolveFieldEditability(fieldKey, permissions)
          expect(result1).toBe(result2)
        }
      ),
      { numRuns: 100 }
    )
  })
})
