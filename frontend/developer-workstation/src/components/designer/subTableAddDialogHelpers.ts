import type { FormRules } from 'element-plus'
import { isTableAuditField } from '@/utils/tableAuditFields'

export type ColumnType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'select'
  | 'radio'
  | 'checkbox'
  | 'switch'
  | 'date'
  | 'datetime'
  | 'upload'
  | 'user'
  | 'department'
  | 'password'
  | 'timerange'
  | 'treeselect'
  | 'colorPicker'
  | 'rate'
  | 'slider'
  | 'tree'
  | 'editor'
  | 'signature'
  | 'transfer'
  | 'cascader'

export interface TreeNode {
  label: string
  value: string | number
  children?: TreeNode[]
  [key: string]: unknown
}

export interface DialogColumn {
  field: string
  label: string
  type?: ColumnType
  required?: boolean
  readonly?: boolean
  /** Element Plus rules from Form Design validate[]; preferred by buildRules over required-only. */
  rules?: Array<Record<string, unknown>>
  /** Form Design Basis / rule default; used by buildInitialRow for Add dialog. */
  defaultValue?: unknown
  placeholder?: string
  minWidth?: number
  options?: Array<{ label: string; value: string | number }>
  props?: {
    action?: string
    accept?: string
    fileNameTargetField?: string
    options?: Array<{ label: string; value: string | number }>
    multiple?: boolean
    precision?: number
    min?: number
    max?: number
    rows?: number
    maxlength?: number
    userType?: 'user' | 'department'
    treeData?: TreeNode[]
    isRange?: boolean
    valueFormat?: string
    startPlaceholder?: string
    endPlaceholder?: string
    checkStrictly?: boolean
    [key: string]: unknown
  }
}

export function isColReadonly(col: Pick<DialogColumn, 'readonly'>): boolean {
  return col.readonly === true
}

function initialValueFor(col: DialogColumn): unknown {
  if (col.defaultValue !== undefined) {
    return typeof col.defaultValue === 'object' && col.defaultValue !== null
      ? JSON.parse(JSON.stringify(col.defaultValue))
      : col.defaultValue
  }
  switch (col.type) {
    case 'number':
      return undefined
    case 'switch':
      return false
    case 'checkbox':
      return []
    case 'date':
    case 'datetime':
    case 'timerange':
      return null
    case 'treeselect':
      return col.props?.multiple ? [] : ''
    case 'rate':
    case 'slider':
      return 0
    case 'colorPicker':
      return ''
    case 'tree':
      return []
    case 'transfer':
      return []
    case 'cascader':
      return []
    case 'editor':
      return ''
    case 'signature':
      return ''
    default:
      return ''
  }
}

export function buildInitialRow(columns: DialogColumn[]): Record<string, unknown> {
  const row: Record<string, unknown> = {}
  for (const col of columns) {
    row[col.field] = initialValueFor(col)
  }
  return row
}

function isEmptyFormValue(value: unknown): boolean {
  if (value == null) return true
  if (typeof value === 'string' && value.trim() === '') return true
  return false
}

/** Keep seeded PK/FK/runtime values when form-create or empty inputs omit them on save. */
export function mergeFormRowWithSeed(
  seed: Record<string, unknown> | null | undefined,
  form: Record<string, unknown>,
): Record<string, unknown> {
  const row = { ...form }
  if (!seed) return row
  for (const [key, seedVal] of Object.entries(seed)) {
    if (isEmptyFormValue(row[key]) && !isEmptyFormValue(seedVal)) {
      row[key] = seedVal
    }
  }
  return row
}

export function buildRules(columns: DialogColumn[]): FormRules {
  const rules: FormRules = {}
  for (const col of columns) {
    if (col.readonly || isTableAuditField(col.field)) continue
    if (Array.isArray(col.rules) && col.rules.length > 0) {
      rules[col.field] = col.rules as FormRules[string]
      continue
    }
    if (col.required) {
      const trigger =
        col.type === 'select' || col.type === 'date' || col.type === 'datetime' || col.type === 'checkbox' || col.type === 'timerange' || col.type === 'treeselect'
        || col.type === 'cascader' || col.type === 'transfer' || col.type === 'switch'
          ? 'change'
          : 'blur'
      if (col.type === 'switch') {
        rules[col.field] = [{
          type: 'boolean',
          required: true,
          message: `${col.label} is required`,
          trigger,
        }]
      } else {
        rules[col.field] = [{ required: true, message: `${col.label} is required`, trigger }]
      }
    }
  }
  return rules
}

export const CONTROL_TYPE_MAP: Record<NonNullable<ColumnType> | 'text', string> = {
  text: 'ElInput',
  textarea: 'ElInput',
  number: 'ElInputNumber',
  select: 'ElSelect',
  radio: 'ElRadioGroup',
  checkbox: 'ElCheckboxGroup',
  switch: 'ElSwitch',
  date: 'ElDatePicker',
  datetime: 'ElDatePicker',
  upload: 'ElUpload',
  user: 'ElInput',
  department: 'ElInput',
  password: 'ElInput',
  timerange: 'ElTimePicker',
  treeselect: 'ElTreeSelect',
  colorPicker: 'ElColorPicker',
  rate: 'ElRate',
  slider: 'ElSlider',
  tree: 'ElTree',
  editor: 'ElInput',
  signature: 'ElInput',
  transfer: 'ElTransfer',
  cascader: 'ElCascader',
}

export function resolveControlComponent(col: DialogColumn): string {
  return CONTROL_TYPE_MAP[col.type ?? 'text']
}
