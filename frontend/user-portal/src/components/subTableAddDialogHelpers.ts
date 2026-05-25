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

/** Stored upload path/URL from the platform file service. */
export function isStoredFileUrl(value: unknown): value is string {
  if (typeof value !== 'string' || !value.trim()) return false
  return /\/upload\/files\//i.test(value.trim())
}

/** DB / designer field names that store file URLs (not display-name companion columns). */
export function isLikelyFileStorageFieldName(fieldName: string): boolean {
  const f = String(fieldName || '').trim().toLowerCase()
  if (!f) return false
  if (f === 'file_name' || f === 'filename' || f.endsWith('_name')) return false
  return f === 'file' || f === 'fileupload' || f === 'attachment' || f.endsWith('_file')
}

/** Whether a sub-table column should render as upload (dialog + table). */
export function isUploadColumn(
  col: Pick<DialogColumn, 'type' | 'field'>,
  cellValue?: unknown,
): boolean {
  if (col.type === 'upload') return true
  if (isStoredFileUrl(cellValue)) return true
  return isLikelyFileStorageFieldName(col.field)
}

export function inferColumnTypeFromFieldAndValue(
  fieldName: string,
  sampleValue?: unknown,
): ColumnType | undefined {
  if (isStoredFileUrl(sampleValue)) return 'upload'
  if (isLikelyFileStorageFieldName(fieldName)) return 'upload'
  return undefined
}

/**
 * Infer portal column type from sub-list view field metadata when subForm rules are absent
 * (common for nested sub-tables that only define subListViews columns).
 */
export function resolveListColumnFieldType(
  column: { dataType?: string; fieldType?: string; fieldName?: string },
  fieldRule?: { type?: string } | null,
  baseColumn?: { type?: string } | null,
): ColumnType | undefined {
  if (baseColumn?.type && baseColumn.type !== 'text') return baseColumn.type as ColumnType
  if (fieldRule?.type === 'upload') return 'upload'
  const dt = String(column.dataType || column.fieldType || '').toUpperCase()
  if (dt === 'FILE') return 'upload'
  if (column.fieldName && isLikelyFileStorageFieldName(column.fieldName)) return 'upload'
  if (baseColumn?.type) return baseColumn.type as ColumnType
  return undefined
}

const DEFAULT_UPLOAD_PROPS = {
  action: '/api/v1/upload',
  accept: '.jpg,.jpeg,.png,.pdf,.docx,.xlsx',
  multiple: false,
} as const

/** Merge a subListViews field column with optional subForm rule/base column metadata. */
export function mergeListViewFieldColumn(
  column: {
    fieldName: string
    comment?: string
    columnLabel?: string
    minWidth?: number
    dataType?: string
    fieldType?: string
  },
  baseColumn?: Partial<DialogColumn> | null,
  fieldRule?: { type?: string; title?: string; props?: Record<string, unknown> } | null,
): DialogColumn {
  const type = resolveListColumnFieldType(column, fieldRule, baseColumn)
  const label = column.comment || column.columnLabel || baseColumn?.label || fieldRule?.title || column.fieldName
  const minWidth = column.minWidth || baseColumn?.minWidth || (type === 'upload' ? 180 : 100)
  const props: Record<string, unknown> = { ...(baseColumn?.props || {}) }
  if (type === 'upload') {
    if (props.action == null) props.action = fieldRule?.props?.action ?? DEFAULT_UPLOAD_PROPS.action
    if (props.accept == null) props.accept = fieldRule?.props?.accept ?? DEFAULT_UPLOAD_PROPS.accept
    if (props.multiple == null) props.multiple = fieldRule?.props?.multiple ?? DEFAULT_UPLOAD_PROPS.multiple
    if (fieldRule?.props?.fileNameTargetField != null) {
      props.fileNameTargetField = fieldRule.props.fileNameTargetField
    }
  }
  return {
    ...(baseColumn || {}),
    field: column.fieldName,
    label,
    ...(type ? { type } : {}),
    minWidth,
    ...(Object.keys(props).length > 0 ? { props } : {}),
  }
}

export type RelationFieldDef = {
  fieldName?: string
  description?: string
  comment?: string
  dataType?: string
  sortOrder?: number
}

/** Map dw_field_definitions / dataTables dataType to portal sub-table column type (aligns with developer-workstation preview). */
export function mapRelationFieldDataTypeToColumnType(dataType: string): ColumnType | undefined {
  const dt = (dataType || '').toUpperCase()
  if (dt === 'FILE') return 'upload'
  if (
    dt.includes('INT')
    || dt === 'BIGINT'
    || dt.includes('DECIMAL')
    || dt.includes('NUMERIC')
    || dt.includes('FLOAT')
    || dt.includes('DOUBLE')
  ) {
    return 'number'
  }
  if (dt === 'DATE') return 'date'
  if (dt.includes('TIMESTAMP') || dt === 'DATETIME') return 'datetime'
  if (dt === 'BOOLEAN' || dt === 'BOOL') return 'switch'
  return undefined
}

/** KK / shared attachment table (dw_table_definitions.id = 74) when designer subListViews are empty on copied forms. */
export const SHARED_ATTACHMENT_RELATION_TABLE_ID = 74

export function defaultAttachmentListColumns(): DialogColumn[] {
  return [
    { field: 'id', label: 'id', minWidth: 100 },
    { field: 'main_id', label: 'main_id', minWidth: 100 },
    mergeListViewFieldColumn(
      { fieldName: 'file', comment: 'file', dataType: 'FILE' },
      { field: 'file', label: 'file', minWidth: 180 },
      null,
    ),
  ]
}

/** Fallback columns from relation-table field definitions when subListViews / subForm are empty (e.g. attachment on copied forms). */
export function deriveColumnsFromRelationFieldDefinitions(fields: RelationFieldDef[]): DialogColumn[] {
  return [...fields]
    .sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0))
    .map(f => {
      const fieldName = String(f.fieldName ?? '').trim()
      if (!fieldName) return null
      const type = mapRelationFieldDataTypeToColumnType(String(f.dataType ?? ''))
      const label = String(f.description || f.comment || fieldName)
      if (type === 'upload') {
        return mergeListViewFieldColumn(
          { fieldName, comment: label, dataType: 'FILE' },
          { field: fieldName, label, minWidth: 180 },
          null,
        )
      }
      return {
        field: fieldName,
        label,
        ...(type ? { type } : {}),
        minWidth: 100,
      }
    })
    .filter((col): col is DialogColumn => col != null)
}

/** Index relation-table field definitions from function-unit {@code dataTables} content items. */
export function buildRelationTableFieldIndexFromDataTables(
  dataTables: unknown[] | undefined | null,
): Map<number, RelationFieldDef[]> {
  const out = new Map<number, RelationFieldDef[]>()
  if (!Array.isArray(dataTables)) return out
  for (const item of dataTables) {
    if (!item || typeof item !== 'object') continue
    const rec = item as Record<string, unknown>
    let parsed: Record<string, unknown> = {}
    try {
      const raw = rec.data
      parsed =
        typeof raw === 'string'
          ? JSON.parse(raw || '{}')
          : raw && typeof raw === 'object'
            ? (raw as Record<string, unknown>)
            : {}
    } catch {
      continue
    }
    const fields = (parsed.fieldDefinitions ?? parsed.fields) as RelationFieldDef[] | undefined
    if (!Array.isArray(fields) || fields.length === 0) continue
    const tid = Number(parsed.id ?? parsed.tableId ?? rec.sourceId)
    if (Number.isFinite(tid)) out.set(tid, fields)
  }
  return out
}

function formHasSubTableSchemaForBinding(
  formConfig: Record<string, unknown>,
  subForms: Record<string, unknown>,
  bindingId: number | string,
): boolean {
  const sid = String(bindingId)
  const sf = (subForms[bindingId] ?? subForms[sid]) as { rule?: unknown[] } | undefined
  if (sf?.rule && Array.isArray(sf.rule) && sf.rule.length > 0) return true
  const subListViews = formConfig.subListViews as Record<string, { columns?: unknown[] }> | undefined
  const lv = subListViews?.[bindingId] ?? subListViews?.[sid]
  return !!(lv?.columns && Array.isArray(lv.columns) && lv.columns.length > 0)
}

/**
 * Copied BPMN forms often assign a new bindingId with empty subListViews while another form in the same FU
 * already configured list/subForm schema for the same physical table ({@code tableId}).
 */
export function resolveSubTableSchemaByTableId(
  tableId: number,
  contentForms: unknown[] | undefined | null,
  excludeBindingId?: number | null,
): { formConfig: Record<string, any>; subForms: Record<string, any>; bindingId: number } | null {
  if (!Number.isFinite(tableId) || !Array.isArray(contentForms)) return null
  for (const f of contentForms) {
    if (!f || typeof f !== 'object') continue
    const form = f as Record<string, unknown>
    let formConfig: Record<string, any> = {}
    try {
      const raw = form.data
      formConfig =
        typeof raw === 'string'
          ? JSON.parse(raw || '{}')
          : raw && typeof raw === 'object'
            ? (raw as Record<string, any>)
            : {}
    } catch {
      continue
    }
    const subForms = (formConfig.subForms || {}) as Record<string, any>
    const tbs = (form.tableBindings || []) as Array<{ bindingId?: number | string; tableId?: number | null }>
    for (const b of tbs) {
      if (b?.tableId == null || Number(b.tableId) !== Number(tableId)) continue
      const bid = b.bindingId
      if (bid == null || bid === '') continue
      if (excludeBindingId != null && Number(bid) === Number(excludeBindingId)) continue
      if (!formHasSubTableSchemaForBinding(formConfig, subForms, bid)) continue
      return { formConfig, subForms, bindingId: Number(bid) }
    }
  }
  return null
}

/** Upgrade inferred/plain-text columns to upload when field metadata or sample values indicate FILE storage. */
export function normalizeSubTableColumns(
  columns: DialogColumn[],
  sampleRows?: Array<Record<string, unknown>>,
): DialogColumn[] {
  const row0 = sampleRows?.[0]
  return columns.map(col => {
    if (col.type === 'upload') return col
    const sample = row0?.[col.field]
    if (!isUploadColumn(col, sample)) return col
    return mergeListViewFieldColumn(
      { fieldName: col.field, comment: col.label, dataType: 'FILE' },
      col,
      null,
    )
  })
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
