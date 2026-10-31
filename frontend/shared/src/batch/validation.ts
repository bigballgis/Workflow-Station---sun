import type { BatchEditableField } from './fields'

export interface BatchFieldError { row: number; field: string; label: string }

/** Validates the concrete supported controls without coercing false, zero or empty into one value. */
export function firstBatchFieldError(rows: Array<Record<string, unknown>>, fields: BatchEditableField[]): BatchFieldError | null {
  for (let index = 0; index < rows.length; index++) {
    for (const field of fields) {
      const value = rows[index]![field.field]
      if (invalidValue(value, field)) return { row: index + 1, field: field.field, label: field.label }
    }
  }
  return null
}

function invalidValue(value: unknown, field: BatchEditableField): boolean {
  const empty = value == null || value === ''
  if (empty) return field.required
  if (field.type === 'lookup') return typeof value !== 'object' || Array.isArray(value)
  if (field.validators?.some(rule => invalidValidator(value, rule))) return true
  if (field.type === 'switch') return typeof value !== 'boolean'
  if (field.type === 'number' || field.type === 'inputNumber') {
    return typeof value !== 'number' || !Number.isFinite(value)
      || (field.min != null && value < field.min) || (field.max != null && value > field.max)
  }
  if (field.type === 'select') return !field.options.some(option => Object.is(option.value, value))
  return typeof value !== 'string' || (field.maxlength != null && value.length > field.maxlength)
}

function invalidValidator(value: unknown, rule: NonNullable<BatchEditableField['validators']>[number]): boolean {
  const type = rule.type
  if (type === 'string' && typeof value !== 'string') return true
  if (type === 'number' && (typeof value !== 'number' || !Number.isFinite(value))) return true
  if (type === 'integer' && (typeof value !== 'number' || !Number.isInteger(value))) return true
  if (type === 'boolean' && typeof value !== 'boolean') return true
  if (type === 'date' && (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value))) return true
  const magnitude = typeof value === 'number' ? value : typeof value === 'string' ? value.length : null
  return magnitude !== null && ((rule.min != null && magnitude < rule.min)
    || (rule.max != null && magnitude > rule.max) || (rule.len != null && magnitude !== rule.len))
}
