export type BatchEditableField = {
  field: string
  label: string
  type: string
  options: Array<{ label: string; value: unknown }>
  required: boolean
  defaultValue?: unknown
  min?: number
  max?: number
  maxlength?: number
  validators?: Array<{ type?: string; min?: number; max?: number; len?: number }>
}

const supportedEditTypes = new Set(['input', 'text', 'inputNumber', 'number', 'switch', 'select', 'date', 'datePicker', 'datetime'])

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function optionList(value: unknown): Array<{ label: string; value: unknown }> {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (item !== null && typeof item === 'object' && !Array.isArray(item)) {
      const option = item as Record<string, unknown>
      const optionValue = option.value ?? option.label
      return optionValue === undefined ? [] : [{ label: String(option.label ?? optionValue), value: optionValue }]
    }
    return [{ label: String(item), value: item }]
  })
}

/**
 * Flattens only field-bearing rules that the first Batch Edit renderer can express.
 * A field-bearing unsupported node invalidates the whole workspace rather than being
 * silently omitted. Layout-only containers are traversed.
 */
export function extractBatchFields(rules: unknown[]): BatchEditableField[] | null {
  const fields: BatchEditableField[] = []
  const seen = new Set<string>()
  let invalid = false
  const visit = (value: unknown) => {
    if (Array.isArray(value)) return value.forEach(visit)
    const rule = record(value)
    if (!rule) return
    if (rule.type === 'miAssignment') return // Dedicated Assignment intent, never an ordinary editable column.
    const field = typeof rule.field === 'string' ? rule.field.trim() : ''
    if (field) {
      const type = typeof rule.type === 'string' ? rule.type : 'input'
      if (!supportedEditTypes.has(type) || seen.has(field)) {
        invalid = true
        return
      }
      seen.add(field)
      const props = record(rule.props)
      fields.push({
        field,
        type,
        label: typeof rule.title === 'string' && rule.title.trim() ? rule.title : field,
        options: optionList(props?.options ?? rule.options),
        required: rule.required === true || (Array.isArray(rule.validate) && rule.validate.some(item => record(item)?.required === true)),
        defaultValue: rule.value,
        ...(Array.isArray(rule.validate) ? { validators: rule.validate as NonNullable<BatchEditableField['validators']> } : {}),
        ...(typeof props?.min === 'number' ? { min: props.min } : {}),
        ...(typeof props?.max === 'number' ? { max: props.max } : {}),
        ...(typeof props?.maxlength === 'number' ? { maxlength: props.maxlength } : {}),
      })
    }
    Object.values(rule).forEach(child => {
      if (child !== rule.props && (Array.isArray(child) || record(child))) visit(child)
    })
  }
  visit(rules)
  return invalid ? null : fields
}
