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
  | 'lookup'

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
    tableId?: number
    searchFields?: string[]
    displayField?: string
    displayFields?: string[]
    viewFields?: Array<Record<string, unknown>>
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
      case 'lookup':
        row[col.field] = null
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
        || col.type === 'cascader' || col.type === 'transfer' || col.type === 'lookup'
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
  lookup: 'LookupField',
}

export function resolveControlComponent(col: DialogColumn): string {
  return CONTROL_TYPE_MAP[col.type ?? 'text']
}

/** Sub-table cell may store a user id string or a user object ({ id, name, ... }). */
export function extractUserIdFromCellValue(raw: unknown): string {
  if (raw == null) return ''
  if (typeof raw === 'string' || typeof raw === 'number') return String(raw).trim()
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    const o = raw as Record<string, unknown>
    const idPart = o.id ?? o.userId
    if (idPart != null && typeof idPart !== 'object') return String(idPart).trim()
  }
  return ''
}

/**
 * Portal / relation APIs often return user rows as snake_case objects (id, full_name, display_name, …).
 * Prefer full_name / name for plain-text cells; use {@link userObjectTagDisplayString} for lookup-style tag (usually id).
 */
export function unwrapUserLikeValueToDisplayString(rawValue: unknown): string {
  if (rawValue === null || rawValue === undefined) return '-'
  if (typeof rawValue !== 'object' || Array.isArray(rawValue)) {
    return String(rawValue)
  }
  const o = rawValue as Record<string, unknown>
  const preferKeys = [
    'full_name',
    'fullName',
    'displayName',
    'display_name',
    'name',
    'username',
    'email',
    'label',
    'title'
  ] as const
  for (const k of preferKeys) {
    const v = o[k]
    if (v != null && typeof v !== 'object') {
      const s = String(v).trim()
      if (s && s !== '-') return s
    }
  }
  const idVal = o.id ?? o.userId
  if (idVal != null && typeof idVal !== 'object') {
    const s = String(idVal).trim()
    if (s) return s
  }
  return '-'
}

/** Lookup pill: show primary id (matches assignee / user snapshot UX in designer preview). */
export function userObjectTagDisplayString(rawValue: unknown): string {
  if (rawValue === null || rawValue === undefined) return '-'
  if (typeof rawValue !== 'object' || Array.isArray(rawValue)) {
    return unwrapUserLikeValueToDisplayString(rawValue)
  }
  const o = rawValue as Record<string, unknown>
  const idVal = o.id ?? o.userId
  if (idVal != null && typeof idVal !== 'object') {
    const s = String(idVal).trim()
    if (s) return s
  }
  return unwrapUserLikeValueToDisplayString(rawValue)
}

export function isUserSnapshotLikeObject(raw: unknown): boolean {
  if (raw == null || typeof raw !== 'object' || Array.isArray(raw)) return false
  const o = raw as Record<string, unknown>
  const keys = Object.keys(o)
  if (keys.length < 2) return false
  const hasId = o.id != null && typeof o.id !== 'object'
  const hasHints =
    o.username != null ||
    o.full_name != null ||
    o.fullName != null ||
    o.email != null ||
    o.display_name != null ||
    o.displayName != null
  return !!(hasId && hasHints)
}

export interface UserSnapshotViewField {
  key: string
  label: string
}

/** Ordered fields for el-descriptions (keys must exist on row object for cell binding). */
export function userSnapshotViewFieldsFromRow(raw: unknown): UserSnapshotViewField[] {
  if (!isUserSnapshotLikeObject(raw)) return []
  const o = raw as Record<string, unknown>
  const preferredOrder = [
    'id',
    'username',
    'display_name',
    'displayName',
    'full_name',
    'fullName',
    'email',
    'employee_id',
    'employeeId',
    'status',
    'language'
  ]
  const out: UserSnapshotViewField[] = []
  const seen = new Set<string>()
  for (const k of preferredOrder) {
    if (seen.has(k) || !(k in o) || o[k] === undefined) continue
    out.push({ key: k, label: k })
    seen.add(k)
  }
  return out
}

export function formatUserSnapshotCellValue(val: unknown): string {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'object') return '-'
  const s = String(val).trim()
  return s || '-'
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
    if (!options) return unwrapUserLikeValueToDisplayString(rawValue)
    const opt = options.find((o) => o.value === rawValue)
    return opt ? opt.label : unwrapUserLikeValueToDisplayString(rawValue)
  }

  if (col.type === 'checkbox') {
    if (!Array.isArray(rawValue) || !options) return unwrapUserLikeValueToDisplayString(rawValue)
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
    return unwrapUserLikeValueToDisplayString(rawValue)
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

  if (col.type === 'lookup') {
    if (typeof rawValue === 'object') {
      const row = rawValue as Record<string, unknown>
      const displayField = col.props?.displayField
      if (displayField && row[displayField] != null) {
        return unwrapUserLikeValueToDisplayString(row[displayField])
      }
      const displayFields = col.props?.displayFields || []
      const values = displayFields
        .map(field => row[field])
        .filter(value => value != null && String(value).trim() !== '')
      if (values.length > 0) {
        return values.map(String).join(' / ')
      }
      const first = Object.values(row).find(value => value != null && String(value).trim() !== '')
      return first != null ? String(first) : '-'
    }
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

  if (col.type === 'user' || col.type === 'department') {
    return unwrapUserLikeValueToDisplayString(rawValue)
  }

  if (typeof rawValue === 'object' && !Array.isArray(rawValue)) {
    return unwrapUserLikeValueToDisplayString(rawValue)
  }

  return String(rawValue)
}
