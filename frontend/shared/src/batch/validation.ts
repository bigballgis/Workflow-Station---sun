import type { BatchEditableField } from './fields'
import { fieldValidatorError } from '../formEvents/fieldValidation'

export interface BatchFieldError { row: number; field: string; label: string; message?: string }

export function firstBatchFieldError(rows: Array<Record<string, unknown>>, fields: BatchEditableField[]): BatchFieldError | null {
  for (let index = 0; index < rows.length; index++) {
    for (const field of fields) {
      if (field.hidden || field.display === false) continue
      const value = rows[index]![field.field]
      const error = { row: index + 1, field: field.field, label: field.label }
      const empty = value == null || value === '' || (Array.isArray(value) && !value.length)
      if (empty) {
        if (field.required) {
          const required = field.validators?.find(entry => entry.required === true || entry.mode === 'required')
          return typeof required?.message === 'string' ? { ...error, message: required.message } : error
        }
        continue
      }
      if (field.type === 'lookup') {
        if (typeof value !== 'object' || Array.isArray(value)) return error
        continue
      }
      for (const entry of field.validators ?? []) {
        const message = fieldValidatorError(value, entry, field.label)
        if (message) return { ...error, message }
      }
      const props = field.props ?? {}
      if (field.type === 'switch') {
        if (!Object.is(value, props.activeValue ?? true) && !Object.is(value, props.inactiveValue ?? false)) return error
      } else if (field.type === 'number' || field.type === 'inputNumber') {
        if (typeof value !== 'number' || !Number.isFinite(value) || (field.min != null && value < field.min) || (field.max != null && value > field.max)) return error
      } else if (field.type === 'select') {
        if (props.multiple === true && !Array.isArray(value)) return error
        if (props.multiple !== true && Array.isArray(value)) return error
        const selected = Array.isArray(value) ? value : [value]
        if (props.allowCreate !== true && selected.some(item => !field.options.some(option => Object.is(option.value, item)))) return error
      } else if (String(props.type).endsWith('range') && ['date', 'datePicker', 'datetime'].includes(field.type)) {
        if (!Array.isArray(value) || value.length !== 2 || value.some(item => item == null || item === '')) return error
      } else if (['date', 'datePicker', 'datetime'].includes(field.type)) {
        if (!['string', 'number'].includes(typeof value) && !(value instanceof Date)) return error
      } else if (typeof value !== 'string' || (field.maxlength != null && value.length > field.maxlength)) return error
    }
  }
  return null
}
