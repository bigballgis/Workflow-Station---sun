/**
 * Normalize form-create designer validation for Preview / native form-create runtime.
 * Mirrors user-portal mapping: `$required` + validate[] → async-validator entries with triggers.
 */

import { getRuleChildren } from '@/utils/formDesigner'

function isTruthyRequiredFlag(value: unknown): boolean {
  if (value === true) return true
  if (typeof value === 'string') return value.trim().length > 0
  return false
}

export function isFormCreateRuleRequired(rule: Record<string, unknown>): boolean {
  if (isTruthyRequiredFlag(rule.$required) || isTruthyRequiredFlag(rule.required)) return true
  const validate = rule.validate
  if (!Array.isArray(validate)) return false
  return validate.some((item) => {
    if (!item || typeof item !== 'object') return false
    const entry = item as Record<string, unknown>
    return entry.required === true || entry.mode === 'required'
  })
}

function defaultValidateTrigger(fieldType?: string): 'blur' | 'change' {
  if (fieldType === 'select' || fieldType === 'checkbox' || fieldType === 'radio') return 'change'
  return 'blur'
}

function normalizeValidateEntry(
  item: Record<string, unknown>,
  fieldType?: string,
): Record<string, unknown> {
  const entry: Record<string, unknown> = { ...item }
  if (!entry.trigger) entry.trigger = defaultValidateTrigger(fieldType)
  return entry
}

/** Ensure rule.validate is complete for form-create native / adapter validation. */
export function ensureFormCreateRuleValidation(rule: Record<string, unknown>): boolean {
  if (!rule.field) return false
  const fieldType = rule.type != null ? String(rule.type) : undefined
  let changed = false

  let validate = Array.isArray(rule.validate)
    ? rule.validate.map((item) => {
        if (!item || typeof item !== 'object') return item
        const normalized = normalizeValidateEntry(item as Record<string, unknown>, fieldType)
        if (normalized !== item) changed = true
        return normalized
      })
    : []

  if (validate.length !== (Array.isArray(rule.validate) ? rule.validate.length : 0)) {
    changed = true
  }

  const hasRequiredEntry = validate.some((entry) => {
    if (!entry || typeof entry !== 'object') return false
    const item = entry as Record<string, unknown>
    return item.required === true || item.mode === 'required'
  })
  if (isFormCreateRuleRequired(rule) && !hasRequiredEntry) {
    validate.unshift({
      required: true,
      trigger: defaultValidateTrigger(fieldType),
    })
    changed = true
  }

  if (validate.length > 0) {
    if (rule.validate !== validate) {
      rule.validate = validate
      changed = true
    }
  }

  return changed
}

export function ensureFormCreateRulesValidationDeep(rules: unknown[]): boolean {
  if (!Array.isArray(rules)) return false
  let changed = false

  function walk(items: unknown[]) {
    for (const raw of items) {
      if (!raw || typeof raw !== 'object') continue
      const rule = raw as Record<string, unknown>
      if (ensureFormCreateRuleValidation(rule)) changed = true
      const children = getRuleChildren(rule)
      if (children.length) walk(children)
    }
  }

  walk(rules)
  return changed
}
