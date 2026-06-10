import type { FormField } from '@/components/formRendererHelpers'
import {
  bindFormCreateValidatorForElementPlus,
  createFieldKeyResolver,
  isEmptyFormCreateHandler,
} from '@/utils/formCreateEventRuntime'

/** Deferred designer validator — resolved in FormRenderer when form data is available. */
export const FORM_CREATE_VALIDATOR_SOURCE_KEY = '__formCreateValidatorSource'
export const FORM_CREATE_VALIDATOR_ADAPTER_KEY = '__formCreateValidatorAdapter'



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

  if (fieldType === 'select' || fieldType === 'checkbox' || fieldType === 'radio') return 'change'

  return 'blur'

}



function isNumericFieldType(fieldType?: string): boolean {

  return fieldType === 'number'

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

    return { ...baseEntry(item, fieldType), required: true }

  }



  const mode = typeof item.mode === 'string' ? item.mode : undefined

  if (mode) {

    const trigger = item.trigger ?? defaultValidateTrigger(fieldType)

    const message = item.message



    switch (mode) {

      case 'required':

        return { required: true, trigger, ...(message != null && message !== '' ? { message } : {}) }

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

        return {

          max: item.max,

          type: isNumericFieldType(fieldType) ? 'number' : 'string',

          trigger,

          ...(message != null && message !== '' ? { message } : {}),

        }

      case 'min':

        return {

          min: item.min,

          type: isNumericFieldType(fieldType) ? 'number' : 'string',

          trigger,

          ...(message != null && message !== '' ? { message } : {}),

        }

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

      case 'integer':

        return { type: 'integer', trigger, ...(message != null && message !== '' ? { message } : {}) }

      case 'number':

        return { type: 'number', trigger, ...(message != null && message !== '' ? { message } : {}) }

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

    mapped.unshift({

      required: true,

      trigger: defaultValidateTrigger(fieldType),

    })

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
    const hasMaxRule = elRules.some(
      (entry) => entry.max != null || entry.len != null,
    )
    if (!hasMaxRule) {
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


