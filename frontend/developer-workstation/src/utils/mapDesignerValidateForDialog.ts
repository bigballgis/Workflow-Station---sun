/**
 * Map Form Design validate[] (fc-designer Validation+) to Element Plus / async-validator
 * rules for SubTable Add/Edit dialogs.
 *
 * Parity with user-portal {@code mapFormCreateValidateToElementPlusRules} for every
 * designer Validation+ mode: len / maxLen / minLen / pattern / uppercase / lowercase /
 * email / url / ip / phone / min / max / positive / negative / integer / number.
 *
 * Custom {@code validator} scripts are skipped — Preview with formRule uses form-create
 * native validation; the Element Plus dialog has no form-create api.
 */

import { isFormCreateRuleRequired } from '@/utils/formCreateValidateRules'

/** Matches @form-create/core adapter validate (IPv4 only). */
const FORM_CREATE_IPV4_REGEX =
  /^(2(5[0-5]{1}|[0-4]\d{1})|[0-1]?\d{1,2})(\.(2(5[0-5]{1}|[0-4]\d{1})|[0-1]?\d{1,2})){3}$/

/** Matches @form-create/core adapter validate (optional +86 / 0086 prefix). */
const FORM_CREATE_PHONE_REGEX = /^(?:(?:\+|00)86)?1[3-9]\d{9}$/

function defaultTrigger(fieldType?: string): 'blur' | 'change' {
  if (
    fieldType === 'select'
    || fieldType === 'checkbox'
    || fieldType === 'radio'
    || fieldType === 'switch'
  ) {
    return 'change'
  }
  return 'blur'
}

function resolveNumericThreshold(raw: unknown): number | null {
  if (typeof raw === 'number' && !Number.isNaN(raw)) return raw
  if (typeof raw === 'string' && raw.trim() !== '') {
    const parsed = Number(raw)
    if (!Number.isNaN(parsed)) return parsed
  }
  return null
}

function inferMode(item: Record<string, unknown>): string | undefined {
  if (typeof item.mode === 'string' && item.mode) return item.mode
  if (item.required === true) return 'required'
  if (item.validator != null) return 'validator'

  const flagModes = [
    'uppercase',
    'lowercase',
    'email',
    'url',
    'ip',
    'phone',
    'positive',
    'negative',
    'integer',
    'number',
  ] as const
  for (const key of flagModes) {
    if (item[key] === true) return key
  }

  const valueModes = ['len', 'maxLen', 'minLen', 'min', 'max', 'pattern'] as const
  for (const key of valueModes) {
    const val = item[key]
    if (val !== undefined && val !== null && val !== '') return key
  }
  return undefined
}

type NumericMode = 'min' | 'max' | 'positive' | 'negative' | 'integer' | 'number'

function buildNumericRule(
  mode: NumericMode,
  threshold: unknown,
  trigger: unknown,
  message: unknown,
): Record<string, unknown> | null {
  const limit = mode === 'min' || mode === 'max' ? resolveNumericThreshold(threshold) : null
  if ((mode === 'min' || mode === 'max') && limit == null) return null

  const resolvedMessage =
    message != null && message !== ''
      ? String(message)
      : mode === 'min' && limit != null
        ? `Must be at least ${limit}`
        : mode === 'max' && limit != null
          ? `Must be at most ${limit}`
          : mode === 'positive'
            ? 'Must be a positive number'
            : mode === 'negative'
              ? 'Must be a negative number'
              : mode === 'integer'
                ? 'Must be an integer'
                : 'Must be a number'

  return {
    trigger,
    message: resolvedMessage,
    validator: (_rule: unknown, value: unknown, callback: (err?: Error) => void) => {
      if (value === '' || value == null) {
        callback()
        return
      }
      const num = Number(value)
      if (Number.isNaN(num)) {
        callback(new Error(resolvedMessage))
        return
      }
      if (mode === 'min' && limit != null && num < limit) {
        callback(new Error(resolvedMessage))
        return
      }
      if (mode === 'max' && limit != null && num > limit) {
        callback(new Error(resolvedMessage))
        return
      }
      if (mode === 'positive' && num <= 0) {
        callback(new Error(resolvedMessage))
        return
      }
      if (mode === 'negative' && num >= 0) {
        callback(new Error(resolvedMessage))
        return
      }
      if (mode === 'integer' && !Number.isInteger(num)) {
        callback(new Error(resolvedMessage))
        return
      }
      callback()
    },
  }
}

function convertEntry(
  item: Record<string, unknown>,
  fieldType?: string,
): Record<string, unknown> | null {
  if (!item || typeof item !== 'object') return null
  const trigger = item.trigger ?? defaultTrigger(fieldType)
  const message = item.message
  const msg = message != null && message !== '' ? { message: String(message) } : {}

  if (item.required === true) {
    return fieldType === 'switch'
      ? { type: 'boolean', required: true, trigger: 'change', ...msg }
      : { required: true, trigger, ...msg }
  }

  const mode = inferMode(item)
  switch (mode) {
    case 'required':
      return fieldType === 'switch'
        ? { type: 'boolean', required: true, trigger: 'change', ...msg }
        : { required: true, trigger, ...msg }
    case 'len':
      return { len: item.len, trigger, ...msg }
    case 'maxLen':
      return { max: item.maxLen, type: 'string', trigger, ...msg }
    case 'minLen':
      return { min: item.minLen, type: 'string', trigger, ...msg }
    case 'max':
      return buildNumericRule('max', item.max, trigger, message)
    case 'min':
      return buildNumericRule('min', item.min, trigger, message)
    case 'positive':
      return buildNumericRule('positive', undefined, trigger, message)
    case 'negative':
      return buildNumericRule('negative', undefined, trigger, message)
    case 'integer':
      return buildNumericRule('integer', undefined, trigger, message)
    case 'number':
      return buildNumericRule('number', undefined, trigger, message)
    case 'pattern':
      return { pattern: item.pattern ?? item[mode], trigger, ...msg }
    case 'email':
      return { type: 'email', trigger, ...msg }
    case 'url':
      return { type: 'url', trigger, ...msg }
    case 'ip': {
      const resolvedMessage =
        message != null && message !== '' ? String(message) : 'Invalid IP address'
      return {
        trigger,
        message: resolvedMessage,
        validator: (_rule: unknown, value: unknown, callback: (err?: Error) => void) => {
          if (value === '' || value == null) {
            callback()
            return
          }
          if (FORM_CREATE_IPV4_REGEX.test(String(value))) {
            callback()
            return
          }
          callback(new Error(resolvedMessage))
        },
      }
    }
    case 'phone': {
      const resolvedMessage =
        message != null && message !== '' ? String(message) : 'Invalid phone number'
      return {
        trigger,
        message: resolvedMessage,
        validator: (_rule: unknown, value: unknown, callback: (err?: Error) => void) => {
          if (value === '' || value == null) {
            callback()
            return
          }
          if (FORM_CREATE_PHONE_REGEX.test(String(value))) {
            callback()
            return
          }
          callback(new Error(resolvedMessage))
        },
      }
    }
    case 'uppercase':
    case 'lowercase': {
      const wantUpper = mode === 'uppercase'
      const resolvedMessage =
        message != null && message !== ''
          ? String(message)
          : wantUpper
            ? 'Must be uppercase'
            : 'Must be lowercase'
      return {
        trigger,
        message: resolvedMessage,
        validator: (_rule: unknown, value: unknown, callback: (err?: Error) => void) => {
          if (value === '' || value == null) {
            callback()
            return
          }
          const text = String(value)
          const ok = wantUpper ? text === text.toUpperCase() : text === text.toLowerCase()
          if (ok) {
            callback()
            return
          }
          callback(new Error(resolvedMessage))
        },
      }
    }
    case 'validator':
      // Element Plus dialog has no form-create api — Preview formRule path handles this.
      return null
    default:
      return null
  }
}

/** Designer rule → Element Plus rules for dialog columns. */
export function mapDesignerValidateForDialog(
  rule: Record<string, unknown>,
  fieldType?: string,
): Array<Record<string, unknown>> {
  const mapped: Array<Record<string, unknown>> = []
  const validate = rule.validate
  if (Array.isArray(validate)) {
    for (const item of validate) {
      if (!item || typeof item !== 'object') continue
      const normalized = convertEntry(item as Record<string, unknown>, fieldType)
      if (normalized) mapped.push(normalized)
    }
  }
  const hasRequired = mapped.some((entry) => entry.required === true)
  if (isFormCreateRuleRequired(rule) && !hasRequired) {
    mapped.unshift(
      fieldType === 'switch'
        ? { type: 'boolean', required: true, trigger: 'change' }
        : { required: true, trigger: defaultTrigger(fieldType) },
    )
  }
  return mapped
}
