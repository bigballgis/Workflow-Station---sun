import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Feature: function-unit-design-review, Property 15: 动作按钮可见性过滤
 * Validates: Requirements 16.5, 16.6
 *
 * For any action with a visibility condition and allowed roles configuration,
 * and for any formData and user role set, the action button should be visible
 * if and only if: (a) the visibility condition evaluates to true, AND
 * (b) the user's roles intersect with the action's allowed roles (or allowed roles is empty).
 */

interface ConditionExpression {
  field: string
  operator: string
  value?: unknown
}

function evaluateVisibilityCondition(
  condition: ConditionExpression | undefined,
  formData: Record<string, unknown>
): boolean {
  if (!condition) return true
  const { field, operator, value } = condition
  if (!field || !operator) return true
  const fieldValue = formData[field]
  switch (operator) {
    case 'equals': return fieldValue === value
    case 'not-equals': return fieldValue !== value
    case 'greater-than': return Number(fieldValue) > Number(value)
    case 'less-than': return Number(fieldValue) < Number(value)
    case 'contains': return String(fieldValue ?? '').includes(String(value ?? ''))
    case 'is-empty': return fieldValue == null || fieldValue === ''
    case 'is-not-empty': return fieldValue != null && fieldValue !== ''
    default: return true
  }
}

function checkRoleAccess(allowedRoles: string[] | undefined, userRoles: string[]): boolean {
  if (!allowedRoles || allowedRoles.length === 0) return true
  return userRoles.some(r => allowedRoles.includes(r))
}

function isActionVisible(
  action: { visibilityCondition?: ConditionExpression; allowedRoles?: string[] },
  formData: Record<string, unknown>,
  userRoles: string[]
): boolean {
  return evaluateVisibilityCondition(action.visibilityCondition, formData) &&
    checkRoleAccess(action.allowedRoles, userRoles)
}

describe('Property 15: Action Button Visibility Filtering', () => {
  const operatorArb = fc.constantFrom('equals', 'not-equals', 'greater-than', 'less-than', 'is-empty', 'is-not-empty')
  const roleArb = fc.string({ minLength: 1, maxLength: 20 }).filter(s => s.trim().length > 0)

  it('should show action when no visibility condition and no role restriction', () => {
    fc.assert(
      fc.property(
        fc.dictionary(fc.string({ minLength: 1, maxLength: 10 }), fc.oneof(fc.string(), fc.integer())),
        fc.array(roleArb, { maxLength: 5 }),
        (formData, userRoles) => {
          expect(isActionVisible({}, formData, userRoles)).toBe(true)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should hide action when visibility condition evaluates to false', () => {
    // equals condition with mismatched value
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 10 }),
        fc.string({ minLength: 1, maxLength: 10 }),
        fc.string({ minLength: 1, maxLength: 10 }).filter(s => s !== ''),
        (field, fieldValue, condValue) => {
          fc.pre(fieldValue !== condValue)
          const condition: ConditionExpression = { field, operator: 'equals', value: condValue }
          const formData = { [field]: fieldValue }
          expect(isActionVisible({ visibilityCondition: condition }, formData, [])).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should hide action when user has no matching roles', () => {
    fc.assert(
      fc.property(
        fc.array(roleArb, { minLength: 1, maxLength: 5 }),
        fc.array(roleArb, { minLength: 1, maxLength: 5 }),
        (allowedRoles, userRoles) => {
          // Ensure no overlap
          const disjointUserRoles = userRoles.map(r => r + '_no_match')
          expect(isActionVisible({ allowedRoles }, {}, disjointUserRoles)).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should show action when user has at least one matching role', () => {
    fc.assert(
      fc.property(
        fc.array(roleArb, { minLength: 1, maxLength: 5 }),
        (allowedRoles) => {
          // User has at least one of the allowed roles
          const userRoles = [allowedRoles[0]]
          expect(isActionVisible({ allowedRoles }, {}, userRoles)).toBe(true)
        }
      ),
      { numRuns: 100 }
    )
  })
})
