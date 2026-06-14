/**
 * BusinessLogicEngine — Field & cross-field validation (Tasks 4.11, 4.13).
 */

import type { CrossFieldRule, ValidationRule } from '../formRendererHelpers'
import type { CrossFieldValidationResult } from './types'

// ─── validateField (Task 4.11) ──────────────────────────────────────────────

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_REGEX = /^1[3-9]\d{9}$/

/**
 * Validate a single field value against an array of ValidationRule.
 * Returns an array of error messages for all failing rules.
 * Generates Element Plus compatible validation rules.
 */
export function validateField(
  value: unknown,
  rules: ValidationRule[],
): string[] {
  const errors: string[] = []

  for (const rule of rules) {
    switch (rule.type) {
      case 'required': {
        if (
          value === null ||
          value === undefined ||
          value === '' ||
          (Array.isArray(value) && value.length === 0)
        ) {
          errors.push(rule.message)
        }
        break
      }

      case 'pattern': {
        if (rule.pattern && value != null && value !== '') {
          try {
            const regex = new RegExp(rule.pattern)
            if (!regex.test(String(value))) {
              errors.push(rule.message)
            }
          } catch {
            console.warn(
              `[BusinessLogicEngine] Invalid regex pattern: "${rule.pattern}"`,
            )
          }
        }
        break
      }

      case 'number': {
        if (value != null && value !== '') {
          const num = Number(value)
          if (isNaN(num)) {
            errors.push(rule.message)
          } else {
            if (rule.min !== undefined && num < rule.min) {
              errors.push(rule.message)
            }
            if (rule.max !== undefined && num > rule.max) {
              errors.push(rule.message)
            }
          }
        }
        break
      }

      case 'email': {
        if (value != null && value !== '' && !EMAIL_REGEX.test(String(value))) {
          errors.push(rule.message)
        }
        break
      }

      case 'phone': {
        if (value != null && value !== '' && !PHONE_REGEX.test(String(value))) {
          errors.push(rule.message)
        }
        break
      }

      case 'custom': {
        // Custom validation uses pattern field as a regex
        if (rule.pattern && value != null && value !== '') {
          try {
            const regex = new RegExp(rule.pattern)
            if (!regex.test(String(value))) {
              errors.push(rule.message)
            }
          } catch {
            console.warn(
              `[BusinessLogicEngine] Invalid custom regex: "${rule.pattern}"`,
            )
          }
        }
        // minLength / maxLength checks
        if (value != null && typeof value === 'string') {
          if (rule.minLength !== undefined && value.length < rule.minLength) {
            errors.push(rule.message)
          }
          if (rule.maxLength !== undefined && value.length > rule.maxLength) {
            errors.push(rule.message)
          }
        }
        break
      }
    }
  }

  // Deduplicate error messages
  return [...new Set(errors)]
}

// ─── validateCrossFields (Task 4.13) ────────────────────────────────────────

/**
 * Evaluate cross-field validation rules.
 * Supports: greater-than, less-than, equals, not-equals, date-after, date-before.
 * Returns errors with targetField and message.
 */
export function validateCrossFields(
  rules: CrossFieldRule[],
  formData: Record<string, unknown>,
): CrossFieldValidationResult {
  const errors: Array<{ targetField: string; message: string }> = []

  for (const rule of rules) {
    if (rule.fields.length < 2) continue

    const [fieldA, fieldB] = rule.fields
    const valA = formData[fieldA]
    const valB = formData[fieldB]

    // Skip if either field is empty/undefined
    if (valA == null || valA === '' || valB == null || valB === '') {
      continue
    }

    let valid = true

    switch (rule.operator) {
      case 'greater-than':
        valid = Number(valA) > Number(valB)
        break
      case 'less-than':
        valid = Number(valA) < Number(valB)
        break
      case 'equals':

        valid = valA == valB
        break
      case 'not-equals':

        valid = valA != valB
        break
      case 'date-after': {
        const dateA = new Date(String(valA)).getTime()
        const dateB = new Date(String(valB)).getTime()
        valid = !isNaN(dateA) && !isNaN(dateB) && dateA > dateB
        break
      }
      case 'date-before': {
        const dateA = new Date(String(valA)).getTime()
        const dateB = new Date(String(valB)).getTime()
        valid = !isNaN(dateA) && !isNaN(dateB) && dateA < dateB
        break
      }
    }

    if (!valid) {
      errors.push({ targetField: rule.targetField, message: rule.message })
    }
  }

  return { valid: errors.length === 0, errors }
}
