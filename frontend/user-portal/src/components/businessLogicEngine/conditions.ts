/**
 * BusinessLogicEngine — Condition evaluation (Task 4.2).
 *
 * Security: NO eval(), NO new Function(). Conditions are evaluated through
 * a custom operator switch only.
 */

import type { ConditionExpression } from '../formRendererHelpers'

// ─── evaluateCondition (Task 4.2) ───────────────────────────────────────────

/**
 * Evaluate a ConditionExpression against formData.
 *
 * Supports operators: equals, not-equals, contains, greater-than, less-than,
 * is-empty, is-not-empty. Supports AND/OR logic with children.
 *
 * If a referenced field doesn't exist in formData, logs a warning and
 * returns true (ignore rule).
 */
export function evaluateCondition(
  condition: ConditionExpression,
  formData: Record<string, unknown>,
): boolean {
  // If the condition has children with a logic operator, evaluate recursively
  if (condition.children && condition.children.length > 0 && condition.logic) {
    const childResults = condition.children.map((child) =>
      evaluateCondition(child, formData),
    )
    if (condition.logic === 'AND') {
      return childResults.every(Boolean)
    }
    // OR
    return childResults.some(Boolean)
  }

  const fieldKey = condition.field
  if (!(fieldKey in formData)) {
    console.warn(
      `[BusinessLogicEngine] Condition references non-existent field "${fieldKey}", ignoring rule.`,
    )
    return true
  }

  const fieldValue = formData[fieldKey]
  const conditionValue = condition.value

  switch (condition.operator) {
    case 'equals':

      return fieldValue == conditionValue
    case 'not-equals':

      return fieldValue != conditionValue
    case 'contains': {
      if (typeof fieldValue === 'string' && conditionValue != null) {
        return fieldValue.includes(String(conditionValue))
      }
      if (Array.isArray(fieldValue) && conditionValue != null) {
        return fieldValue.includes(conditionValue)
      }
      return false
    }
    case 'greater-than':
      return Number(fieldValue) > Number(conditionValue)
    case 'less-than':
      return Number(fieldValue) < Number(conditionValue)
    case 'is-empty':
      return (
        fieldValue === null ||
        fieldValue === undefined ||
        fieldValue === '' ||
        (Array.isArray(fieldValue) && fieldValue.length === 0)
      )
    case 'is-not-empty':
      return !(
        fieldValue === null ||
        fieldValue === undefined ||
        fieldValue === '' ||
        (Array.isArray(fieldValue) && fieldValue.length === 0)
      )
    default:
      return true
  }
}
