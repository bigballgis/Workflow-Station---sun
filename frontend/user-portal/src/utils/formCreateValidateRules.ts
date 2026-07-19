import type { FormField } from '@/components/formRendererHelpers'
import {
  bindFormCreateValidatorForElementPlus,
  createFieldKeyResolver,
  isEmptyFormCreateHandler,
} from '@/utils/formCreateEventRuntime'

/** Deferred designer validator — resolved in FormRenderer when form data is available. */
export const FORM_CREATE_VALIDATOR_SOURCE_KEY = '__formCreateValidatorSource'
export const FORM_CREATE_VALIDATOR_ADAPTER_KEY = '__formCreateValidatorAdapter'



/** Matches @form-create/core adapter validate (IPv4 only). */
const FORM_CREATE_IPV4_REGEX =
  /^(2(5[0-5]{1}|[0-4]\d{1})|[0-1]?\d{1,2})(\.(2(5[0-5]{1}|[0-4]\d{1})|[0-1]?\d{1,2})){3}$/

/** Matches @form-create/core adapter validate (optional +86 / 0086 prefix). */
const FORM_CREATE_PHONE_REGEX = /^(?:(?:\+|00)86)?1[3-9]\d{9}$/

/** form-create designer stores "Is it required" on `$required`, not always in validate[]. */

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

function resolveRulePropsMaxLength(rule: Record<string, unknown>): number | undefined {
  const props = rule.props
  if (!props || typeof props !== 'object') return undefined
  const raw = (props as Record<string, unknown>).maxlength ?? (props as Record<string, unknown>).maxLength
  if (typeof raw === 'number' && raw > 0) return raw
  if (typeof raw === 'string' && raw.trim() !== '') {
    const parsed = Number(raw)
    if (!Number.isNaN(parsed) && parsed > 0) return parsed
  }
  return undefined
}



function defaultValidateTrigger(fieldType?: string): 'blur' | 'change' {

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

function isBooleanSwitchFieldType(fieldType?: string): boolean {
  return fieldType === 'switch'
}

/** async-validator treats bare `required: true` as failing on `false`; boolean fields need `type: 'boolean'`. */
function buildRequiredValidationRule(
  extras: Record<string, unknown> = {},
  fieldType?: string,
): Record<string, unknown> {
  if (isBooleanSwitchFieldType(fieldType)) {
    return {
      type: 'boolean',
      required: true,
      trigger: 'change',
      ...extras,
    }
  }
  return {
    required: true,
    trigger: defaultValidateTrigger(fieldType),
    ...extras,
  }
}



function resolveNumericValidationThreshold(raw: unknown): number | null {
  if (typeof raw === 'number' && !Number.isNaN(raw)) return raw
  if (typeof raw === 'string' && raw.trim() !== '') {
    const parsed = Number(raw)
    if (!Number.isNaN(parsed)) return parsed
  }
  return null
}

/** Infer validate mode when saved JSON has `min: 1` but no `mode: 'min'` (adapter / legacy shape). */
export function inferFormCreateDesignerValidateMode(
  item: Record<string, unknown>,
): string | undefined {
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

  const valueModes = [
    'len',
    'maxLen',
    'minLen',
    'min',
    'max',
    'pattern',
    'equal',
    'enum',
    'hasKeys',
  ] as const
  for (const key of valueModes) {
    const val = item[key]
    if (val !== undefined && val !== null && val !== '') return key
  }

  return undefined
}

function designerValidateHasMode(
  rule: Record<string, unknown>,
  ...modes: string[]
): boolean {
  const validate = rule.validate
  if (!Array.isArray(validate)) return false
  return validate.some((raw) => {
    if (!raw || typeof raw !== 'object') return false
    const mode = inferFormCreateDesignerValidateMode(raw as Record<string, unknown>)
    return mode != null && modes.includes(mode)
  })
}

/** @form-create/core adapter validate — numeric modes share Number(value) semantics. */
type FormCreateNumericValidationMode =
  | 'min'
  | 'max'
  | 'positive'
  | 'negative'
  | 'integer'
  | 'number'

function defaultFormCreateNumericValidationMessage(
  mode: FormCreateNumericValidationMode,
  threshold: number | null,
): string {
  if (mode === 'min' && threshold != null) return `Must be at least ${threshold}`
  if (mode === 'max' && threshold != null) return `Must be at most ${threshold}`
  switch (mode) {
    case 'positive':
      return 'Must be a positive number'
    case 'negative':
      return 'Must be a negative number'
    case 'integer':
      return 'Must be an integer'
    case 'number':
      return 'Must be a number'
    default:
      return 'Invalid number'
  }
}

function buildFormCreateNumericValidationRule(
  mode: FormCreateNumericValidationMode,
  threshold: unknown,
  trigger: unknown,
  message: unknown,
): Record<string, unknown> | null {
  const limit =
    mode === 'min' || mode === 'max' ? resolveNumericValidationThreshold(threshold) : null
  if ((mode === 'min' || mode === 'max') && limit == null) return null

  const resolvedMessage =
    message != null && message !== ''
      ? String(message)
      : defaultFormCreateNumericValidationMessage(mode, limit)

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



function baseEntry(

  item: Record<string, unknown>,

  fieldType?: string,

): Record<string, unknown> {

  const entry: Record<string, unknown> = {}

  if (item.message != null && item.message !== '') entry.message = item.message

  entry.trigger = item.trigger ?? defaultValidateTrigger(fieldType)

  return entry

}



/**

 * fc-designer Validation panel stores rules as `{ mode: 'maxLen', maxLen: 2, … }`.

 * form-create Preview understands that shape; Element Plus / async-validator needs `{ max: 2, type: 'string' }`.

 */

export function convertFormCreateDesignerValidateEntry(

  item: Record<string, unknown>,

  fieldType?: string,

): Record<string, unknown> | null {

  if (!item || typeof item !== 'object') return null



  if (item.required === true) {

    return buildRequiredValidationRule(baseEntry(item, fieldType), fieldType)

  }



  const mode = inferFormCreateDesignerValidateMode(item)

  if (mode) {

    const trigger = item.trigger ?? defaultValidateTrigger(fieldType)

    const message = item.message



    switch (mode) {

      case 'required':

        return buildRequiredValidationRule(
          {
            trigger,
            ...(message != null && message !== '' ? { message } : {}),
          },
          fieldType,
        )

      case 'len':

        return { len: item.len, trigger, ...(message != null && message !== '' ? { message } : {}) }

      case 'maxLen':

        return {

          max: item.maxLen,

          type: 'string',

          trigger,

          ...(message != null && message !== '' ? { message } : {}),

        }

      case 'minLen':

        return {

          min: item.minLen,

          type: 'string',

          trigger,

          ...(message != null && message !== '' ? { message } : {}),

        }

      case 'max':

        return buildFormCreateNumericValidationRule('max', item.max, trigger, message)

      case 'min':

        return buildFormCreateNumericValidationRule('min', item.min, trigger, message)

      case 'positive':

        return buildFormCreateNumericValidationRule('positive', undefined, trigger, message)

      case 'negative':

        return buildFormCreateNumericValidationRule('negative', undefined, trigger, message)

      case 'pattern':

        return {

          pattern: item.pattern ?? item[mode],

          trigger,

          ...(message != null && message !== '' ? { message } : {}),

        }

      case 'email':

        return { type: 'email', trigger, ...(message != null && message !== '' ? { message } : {}) }

      case 'url':

        return { type: 'url', trigger, ...(message != null && message !== '' ? { message } : {}) }

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

      case 'integer':

        return buildFormCreateNumericValidationRule('integer', undefined, trigger, message)

      case 'number':

        return buildFormCreateNumericValidationRule('number', undefined, trigger, message)

      case 'uppercase':
      case 'lowercase': {
        const wantUpper = mode === 'uppercase'
        const defaultMessage = wantUpper ? 'Must be uppercase' : 'Must be lowercase'
        const resolvedMessage =
          message != null && message !== '' ? String(message) : defaultMessage
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

      case 'validator': {
        const raw = item.validator
        const base = {
          trigger,
          ...(message != null && message !== '' ? { message } : {}),
          ...(item.adapter === true ? { [FORM_CREATE_VALIDATOR_ADAPTER_KEY]: true } : {}),
        }
        if (typeof raw === 'function' || (typeof raw === 'string' && !isEmptyFormCreateHandler(raw))) {
          return { ...base, [FORM_CREATE_VALIDATOR_SOURCE_KEY]: raw }
        }
        return null
      }

      default:

        break

    }

  }

  const deferredValidator = item.validator
  if (
    typeof deferredValidator === 'function'
    || (typeof deferredValidator === 'string' && !isEmptyFormCreateHandler(deferredValidator))
  ) {
    return {
      ...baseEntry(item, fieldType),
      [FORM_CREATE_VALIDATOR_SOURCE_KEY]: deferredValidator,
      ...(item.adapter === true ? { [FORM_CREATE_VALIDATOR_ADAPTER_KEY]: true } : {}),
    }
  }

  const entry: Record<string, unknown> = { ...item }

  delete entry.mode

  delete entry.adapter

  delete entry.maxLen

  delete entry.minLen

  if (!entry.trigger) entry.trigger = defaultValidateTrigger(fieldType)

  if (Object.keys(entry).length <= 1 && entry.trigger) return null

  return entry

}



/** Strip form-create-only keys; map designer modes to async-validator entries. */

function normalizeValidateEntry(

  item: Record<string, unknown>,

  fieldType?: string,

): Record<string, unknown> | null {

  return convertFormCreateDesignerValidateEntry(item, fieldType)

}



/**

 * Map form-create rule.validate + `$required` to Element Plus / async-validator rules.

 */

export function mapFormCreateValidateToElementPlusRules(

  rule: Record<string, unknown>,

  fieldType?: string,

): Array<Record<string, unknown>> {

  const mapped: Array<Record<string, unknown>> = []

  const validate = rule.validate

  if (Array.isArray(validate)) {

    for (const item of validate) {

      if (!item || typeof item !== 'object') continue

      const normalized = normalizeValidateEntry(item as Record<string, unknown>, fieldType)

      if (normalized) mapped.push(normalized)

    }

  }



  const hasRequiredEntry = mapped.some((entry) => entry.required === true)

  if (isFormCreateRuleRequired(rule) && !hasRequiredEntry) {

    mapped.unshift(buildRequiredValidationRule({}, fieldType))

  }



  return mapped

}



/** Apply designer validation onto a portal {@link FormField}. */

export function applyFormCreateValidationToFormField(
  field: FormField,
  rule: Record<string, unknown>,
): void {
  const elRules = mapFormCreateValidateToElementPlusRules(rule, field.type)
  field.required = isFormCreateRuleRequired(rule)

  const propsMaxLength = resolveRulePropsMaxLength(rule)
  if (propsMaxLength != null) {
    field.maxLength = propsMaxLength
    const hasDesignerMaxConstraint = designerValidateHasMode(rule, 'max', 'maxLen')
    const hasMaxRule = elRules.some(
      (entry) => entry.max != null || entry.len != null,
    )
    if (!hasDesignerMaxConstraint && !hasMaxRule) {
      elRules.push({
        max: propsMaxLength,
        type: 'string',
        trigger: defaultValidateTrigger(field.type),
      })
    }
  }

  field.rules = elRules.length > 0 ? elRules : undefined
}

/**
 * Resolve deferred designer validators into Element Plus async-validator functions.
 * Must run where current form values are available (FormRenderer).
 */
export function materializeFormCreateValidationRules(
  rules: Array<Record<string, unknown>> | undefined,
  getFormData: () => Record<string, unknown>,
  getFields?: () => Array<{ key: string; label?: string }>,
): Array<Record<string, unknown>> {
  if (!rules?.length) return []
  const resolveFieldKey = getFields ? createFieldKeyResolver(getFields) : undefined
  return rules.map((rule) => {
    const raw = rule[FORM_CREATE_VALIDATOR_SOURCE_KEY]
    if (raw == null) return rule
    const bound = bindFormCreateValidatorForElementPlus(
      raw,
      getFormData,
      resolveFieldKey,
      { adapter: rule[FORM_CREATE_VALIDATOR_ADAPTER_KEY] === true },
    )
    if (!bound) return rule
    const {
      [FORM_CREATE_VALIDATOR_SOURCE_KEY]: _source,
      [FORM_CREATE_VALIDATOR_ADAPTER_KEY]: _adapter,
      ...rest
    } = rule
    return { ...rest, validator: bound }
  })
}


