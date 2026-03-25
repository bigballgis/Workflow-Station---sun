import type { FormRules } from 'element-plus'

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
    nodeKey?: string
    labelProps?: { label?: string; children?: string }
    [key: string]: unknown
  }
}

export function buildInitialRow(columns: DialogColumn[]): Record<string, unknown> {
  const row: Record<string, unknown> = {}
  for (const col of columns) {
    switch (col.type) {
      case 'number':
        row[col.field] = undefined
        break
      case 'switch':
        row[col.field] = false
        break
      case 'checkbox':
        row[col.field] = []
        break
      case 'date':
      case 'datetime':
      case 'timerange':
        row[col.field] = null
        break
      case 'treeselect':
        row[col.field] = col.props?.multiple ? [] : ''
        break
      case 'rate':
      case 'slider':
        row[col.field] = 0
        break
      case 'colorPicker':
        row[col.field] = ''
        break
      case 'tree':
        row[col.field] = []
        break
      case 'transfer':
        row[col.field] = []
        break
      case 'cascader':
        row[col.field] = []
        break
      case 'editor':
        row[col.field] = ''
        break
      case 'signature':
        row[col.field] = ''
        break
      default:
        // text, textarea, password, radio, select, user, department
        row[col.field] = ''
    }
  }
  return row
}

export function buildRules(columns: DialogColumn[]): FormRules {
  const rules: FormRules = {}
  for (const col of columns) {
    if (col.required) {
      const trigger =
        col.type === 'select' || col.type === 'date' || col.type === 'datetime' || col.type === 'checkbox'
        || col.type === 'cascader' || col.type === 'transfer'
          ? 'change'
          : 'blur'
      rules[col.field] = [{ required: true, message: `${col.label} is required`, trigger }]
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

/**
 * Resolves a raw stored value to a human-readable display string for table cells.
 * - radio/select: maps value → option label
 * - checkbox: maps array of values → comma-separated labels
 * - password: returns masked string '••••••'
 * - timerange: formats [start, end] array as "start - end"
 * - others: converts to string
 */
export function resolveDisplayValue(col: DialogColumn, rawValue: unknown): string {
  if (rawValue === null || rawValue === undefined) return '-'

  const options = col.props?.options ?? col.options

  if (col.type === 'password') {
    return '••••••'
  }

  if (col.type === 'radio' || col.type === 'select') {
    if (!options) return String(rawValue)
    const opt = options.find((o) => o.value === rawValue)
    return opt ? opt.label : String(rawValue)
  }

  if (col.type === 'checkbox') {
    if (!Array.isArray(rawValue) || !options) return String(rawValue)
    return rawValue
      .map((v: unknown) => options.find((o) => o.value === v)?.label ?? v)
      .join(', ')
  }

  if (col.type === 'timerange') {
    if (Array.isArray(rawValue) && rawValue.length === 2) {
      return `${rawValue[0]} - ${rawValue[1]}`
    }
    return String(rawValue)
  }

  if (col.type === 'treeselect') {
    return String(rawValue)
  }

  if (col.type === 'rate') {
    return `${rawValue} ★`
  }

  if (col.type === 'slider') {
    return String(rawValue)
  }

  if (col.type === 'colorPicker') {
    return String(rawValue)
  }

  if (col.type === 'editor') {
    // Strip HTML tags for table display
    return String(rawValue).replace(/<[^>]*>/g, '').substring(0, 100) || '-'
  }

  if (col.type === 'signature') {
    return rawValue ? '[Signature]' : '-'
  }

  if (col.type === 'transfer') {
    if (Array.isArray(rawValue)) return rawValue.join(', ')
    return String(rawValue)
  }

  if (col.type === 'cascader') {
    if (Array.isArray(rawValue)) return rawValue.join(' / ')
    return String(rawValue)
  }

  if (col.type === 'tree') {
    if (!Array.isArray(rawValue)) return String(rawValue)
    const treeData = col.props?.treeData || []
    const nodeKey = col.props?.nodeKey || 'id'
    const labelKey = col.props?.labelProps?.label || 'label'
    const childrenKey = col.props?.labelProps?.children || 'children'
    // Flatten tree to build id→label map
    const labelMap = new Map<string | number, string>()
    const walk = (nodes: TreeNode[]) => {
      for (const n of nodes) {
        const key = n[nodeKey]
        const label = n[labelKey]
        if (key != null && typeof label === 'string') {
          labelMap.set(key as string | number, label)
        }
        const children = n[childrenKey]
        if (Array.isArray(children)) walk(children as TreeNode[])
      }
    }
    walk(treeData)
    return rawValue.map((v: unknown) => labelMap.get(v as string | number) ?? v).join(', ')
  }

  return String(rawValue)
}
