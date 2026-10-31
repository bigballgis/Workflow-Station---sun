import type { FormField } from '@/components/formRendererHelpers'

/** Shared by process, task and request field conversion; retains authored control semantics. */
export function applyDesignerControlProps(field: FormField, rule: Record<string, unknown>): void {
  const props = (rule.props ?? {}) as Record<string, unknown>
  field.sourceRule = rule
  field.placeholder = typeof props.placeholder === 'string' ? props.placeholder : undefined
  if (rule.type === 'input') field.showWordLimit = props.showWordLimit === true
  if (rule.type === 'inputNumber') {
    if (typeof props.step === 'number') field.step = props.step
    if (typeof props.precision === 'number') field.precision = props.precision
    if (typeof props.min === 'number') field.min = props.min
    if (typeof props.max === 'number') field.max = props.max
    if (props.controlsPosition === 'right' || props.controlsPosition === '') field.controlsPosition = props.controlsPosition
  }
  if (rule.type === 'switch') {
    if (typeof props.activeText === 'string') field.activeText = props.activeText
    if (typeof props.inactiveText === 'string') field.inactiveText = props.inactiveText
    for (const key of ['activeValue', 'inactiveValue'] as const) {
      const value = props[key]
      if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') field[key] = value
    }
    const valueType = typeof (props.inactiveValue ?? props.activeValue ?? false)
    if (valueType === 'string' || valueType === 'number') field.rules = field.rules?.map(rule => rule.type === 'boolean' ? { ...rule, type: valueType } : rule)
  }
  if (rule.type === 'select') field.filterable = props.filterable === true
  if (['select', 'radio', 'checkbox', 'transfer'].includes(String(rule.type))) {
    const options = rule.options ?? props.options
    if (Array.isArray(options)) field.options = options.map(option => ({
      label: option.label ?? option.value, value: option.value, ...(option.disabled === true ? { disabled: true } : {}),
    }))
  }
  if (['datePicker', 'DatePicker', 'date-picker', 'el-date-picker'].includes(String(rule.type)) && props.type === 'datetimerange') field.type = 'datetimerange'
  for (const key of ['format', 'valueFormat', 'startPlaceholder', 'endPlaceholder'] as const) {
    if (typeof props[key] === 'string') field[key] = props[key]
  }
}
