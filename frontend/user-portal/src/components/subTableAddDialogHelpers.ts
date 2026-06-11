import type { FormRules } from 'element-plus'
import { legacyBindingIdAliases } from './formRendererHelpers'

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
    // Auto-PK / readonly FK are system-filled; form-create disabled fields often fail required checks.
    if (col.required && !col.readonly) {
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
  const label = column.displayName || column.columnLabel || baseColumn?.label || fieldRule?.title || column.fieldName
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
  displayName?: string
  description?: string
  comment?: string
  dataType?: string
  sortOrder?: number
  isPrimaryKey?: boolean
  isForeignKey?: boolean
  refTableId?: number
  refPrimaryKeyFields?: string[]
  pkGeneration?: Record<string, unknown>
  pkGenerationJson?: Record<string, unknown>
  fkDisplayMode?: string
}

/** Apply designer table display names to list / dialog column labels. */
export function enrichColumnsWithTableFieldDisplayNames(
  columns: DialogColumn[],
  tableId: number | null | undefined,
  fieldIndex: Map<number, RelationFieldDef[]>,
): DialogColumn[] {
  if (tableId == null || !Number.isFinite(Number(tableId))) return columns
  const fields = fieldIndex.get(Number(tableId))
  if (!fields?.length) return columns
  const labelByField = new Map(
    fields
      .filter(f => f.fieldName)
      .map(f => [String(f.fieldName), String(f.displayName || f.fieldName)]),
  )
  return columns.map(col => {
    const label = labelByField.get(col.field)
    return label ? { ...col, label } : col
  })
}

/** Resolve FK/PK metadata: prefer tableBindings payload, fall back to function-unit dataTables. */
export function resolveBindingFieldDefinitions(
  binding: {
    tableId?: number | null
    fieldDefinitions?: Array<Record<string, unknown>>
  },
  fieldIndex: Map<number, RelationFieldDef[]>,
): Array<Record<string, unknown>> {
  const fromBinding = binding.fieldDefinitions
  if (Array.isArray(fromBinding) && fromBinding.length > 0) {
    return fromBinding
  }
  const tableId = binding.tableId != null ? Number(binding.tableId) : NaN
  if (!Number.isFinite(tableId)) return []
  const fields = fieldIndex.get(tableId)
  if (!fields?.length) return []
  return fields
    .filter(f => f.fieldName)
    .map(f => ({
      fieldName: f.fieldName,
      isPrimaryKey: f.isPrimaryKey,
      isForeignKey: f.isForeignKey,
      refTableId: f.refTableId,
      refPrimaryKeyFields: f.refPrimaryKeyFields,
      pkGeneration: f.pkGeneration ?? f.pkGenerationJson,
      pkGenerationJson: f.pkGenerationJson ?? f.pkGeneration,
      fkDisplayMode: f.fkDisplayMode,
    }))
}

/** Build parent table metadata map for ensureParentRowsForChildAdd (PRIMARY + SUB tables). */
export function buildParentTablesByIdFromBindings(
  bindings: Array<{
    tableId?: number | null
    bindingType?: string
    fieldDefinitions?: Array<Record<string, unknown>>
  }>,
  fieldIndex: Map<number, RelationFieldDef[]>,
): Record<number, { fieldDefinitions: Array<Record<string, unknown>> }> {
  const out: Record<number, { fieldDefinitions: Array<Record<string, unknown>> }> = {}
  for (const b of bindings) {
    if (b.bindingType !== 'PRIMARY' && b.bindingType !== 'SUB') continue
    if (b.tableId == null) continue
    const tid = Number(b.tableId)
    const defs = resolveBindingFieldDefinitions(b, fieldIndex)
    if (defs.length > 0) {
      out[tid] = { fieldDefinitions: defs }
    }
  }
  return out
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
      const label = String(f.displayName || fieldName)
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

export type SubListViewColumn = {
  fieldName?: string
  columnType?: string
  dataType?: string
  [key: string]: unknown
}

function isLinkFormListColumn(c: SubListViewColumn): boolean {
  if (!c || typeof c !== 'object') return false
  if (c.columnType === 'linkForm') return true
  if (typeof c.dataType === 'string' && c.dataType.toUpperCase() === 'LINK_FORM') return true
  if (typeof c.fieldName === 'string' && c.fieldName.startsWith('linkForm:')) return true
  return false
}

function countListViewDataColumns(cols: SubListViewColumn[]): number {
  return cols.filter(c => c?.fieldName && !isLinkFormListColumn(c)).length
}

/**
 * Resolve designer list-view columns for a binding. When a binding id was recreated, {@code subListViews}
 * may contain only a PK stub under the new id while {@code subForms} still has the full field set —
 * fall back to sub-form columns (same as process start) instead of rendering a single-column table.
 */
export function resolveSubListViewColumnsForBinding(
  formConfig: Record<string, unknown> | null | undefined,
  bindingId: number | string,
  subFormFieldNames: readonly string[] = [],
): SubListViewColumn[] | null {
  if (!formConfig || typeof formConfig !== 'object') return null
  const stv = formConfig.subListViews as Record<string, { columns?: SubListViewColumn[] }> | undefined
  if (!stv || typeof stv !== 'object') return null
  let direct: SubListViewColumn[] | undefined
  for (const alias of legacyBindingIdAliases(bindingId)) {
    const cols = stv[alias]?.columns ?? stv[String(alias)]?.columns
    if (Array.isArray(cols) && cols.length > 0) {
      direct = cols
      break
    }
  }
  if (!Array.isArray(direct) || direct.length === 0) return null

  const subFormCount = subFormFieldNames.length
  if (subFormCount === 0) return direct

  const dataColCount = countListViewDataColumns(direct)
  const covered = new Set(
    direct
      .filter(c => c?.fieldName && !isLinkFormListColumn(c))
      .map(c => String(c.fieldName)),
  )
  const coversAllSubForm = subFormFieldNames.every(f => covered.has(f))
  if (coversAllSubForm) return direct
  // Intentional partial list (designer picked 2+ columns but not every sub-form field)
  if (dataColCount >= 2) return direct
  // Stale stub under a new binding id — prefer sub-form column derivation
  return null
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

export type ParsedLookupConfig = {
  bindingId?: number
  tableId?: number | null
  tableName?: string
  searchFields?: string[]
  displayFields?: string[]
  selectedDisplayField?: string
  displayField?: string
  filterConditions?: Array<{ fieldName: string; value: string }>
  showBackfillView?: boolean
}

export function parseLookupConfig(raw: unknown): ParsedLookupConfig {
  try {
    const cfg = typeof raw === 'string' ? JSON.parse(raw || '{}') : (raw || {})
    return cfg && typeof cfg === 'object' ? (cfg as ParsedLookupConfig) : {}
  } catch {
    return {}
  }
}

export function getLookupPrimaryKeyFieldFromProps(props?: {
  searchFields?: string[]
  lookupConfig?: unknown
} | null): string {
  const cfg = parseLookupConfig(props?.lookupConfig)
  return String(props?.searchFields?.[0] || cfg.searchFields?.[0] || 'id').trim() || 'id'
}

function pickFirstNonPrimaryDisplayField(
  pkField: string,
  candidates: Array<string | undefined | null>,
): string {
  for (const candidate of candidates) {
    const field = typeof candidate === 'string' ? candidate.trim() : ''
    if (field && field !== pkField) return field
  }
  return ''
}

export function getLookupSelectedDisplayFieldFromProps(props?: {
  selectedDisplayField?: string
  _lookupSelectedDisplayField?: string
  displayField?: string
  displayFields?: string[]
  searchFields?: string[]
  lookupConfig?: unknown
} | null): string {
  if (!props) return ''
  const cfg = parseLookupConfig(props.lookupConfig)
  const pkField = getLookupPrimaryKeyFieldFromProps(props)

  const explicit = [
    props._lookupSelectedDisplayField,
    props.selectedDisplayField,
    cfg.selectedDisplayField,
  ]
    .map(v => (typeof v === 'string' ? v.trim() : ''))
    .find(v => v !== '')
  if (explicit) return explicit

  const fromDisplayFields = pickFirstNonPrimaryDisplayField(pkField, [
    ...(Array.isArray(props.displayFields) ? props.displayFields.map(String) : []),
    ...(Array.isArray(cfg.displayFields) ? cfg.displayFields.map(String) : []),
  ])
  if (fromDisplayFields) return fromDisplayFields

  const fromDisplayField = pickFirstNonPrimaryDisplayField(pkField, [
    cfg.displayField,
    props.displayField,
  ])
  if (fromDisplayField) return fromDisplayField

  // Last resort only when designer explicitly chose PK as the only display field.
  if (Array.isArray(props.displayFields) && props.displayFields.length > 0) {
    return String(props.displayFields[0])
  }
  if (Array.isArray(cfg.displayFields) && cfg.displayFields.length > 0) {
    return String(cfg.displayFields[0])
  }
  if (Array.isArray(props.searchFields) && props.searchFields.length > 0) {
    return String(props.searchFields[0])
  }
  if (Array.isArray(cfg.searchFields) && cfg.searchFields.length > 0) {
    return String(cfg.searchFields[0])
  }
  return ''
}

/** Same priority as LookupField / designer LookupPreview: selectedDisplayField → displayFields[0] → displayField → searchFields[0]. */
export function getLookupSelectedDisplayField(col: DialogColumn): string {
  return getLookupSelectedDisplayFieldFromProps(col.props ?? null)
}

export function buildLookupColumnProps(
  rawLookupConfig: unknown,
  options?: {
    relationViewFields?: Array<Record<string, unknown>>
    dbCfg?: {
      tableId?: number
      searchFields?: string[]
      displayField?: string
      viewFields?: unknown[]
    }
  },
): Record<string, unknown> {
  const lookupCfg = parseLookupConfig(rawLookupConfig)
  const dbCfg = options?.dbCfg
  const selectedDisplayField = lookupCfg.selectedDisplayField || lookupCfg.displayField || ''
  return {
    lookupConfig: typeof rawLookupConfig === 'string' ? rawLookupConfig : JSON.stringify(lookupCfg || {}),
    tableId: lookupCfg.tableId || dbCfg?.tableId || 0,
    searchFields: lookupCfg.searchFields || dbCfg?.searchFields || [],
    displayField: lookupCfg.displayFields?.[0] || dbCfg?.displayField || '',
    displayFields: lookupCfg.displayFields || [],
    selectedDisplayField,
    _lookupSelectedDisplayField: selectedDisplayField,
    filterConditions: Array.isArray(lookupCfg.filterConditions) ? lookupCfg.filterConditions : [],
    viewFields:
      lookupCfg.showBackfillView === false
        ? []
        : (options?.relationViewFields || dbCfg?.viewFields || []),
    showBackfillView: lookupCfg.showBackfillView !== false,
  }
}

function lookupCellValue(row: Record<string, unknown>, field: string): unknown {
  if (!field) return undefined
  const val = row[field]
  if (val != null && String(val).trim() !== '') return val
  return undefined
}

/** Tag/cell label for lookup rows — mirrors designer LookupPreview / LookupField. */
export function resolveLookupCellTagText(
  lookupProps: {
    selectedDisplayField?: string
    _lookupSelectedDisplayField?: string
    displayField?: string
    displayFields?: string[]
    searchFields?: string[]
    lookupConfig?: unknown
  } | null | undefined,
  row: Record<string, unknown> | null | undefined,
): string {
  if (!row || typeof row !== 'object' || Array.isArray(row)) return '-'

  const cfg = parseLookupConfig(lookupProps?.lookupConfig)
  const pkField = getLookupPrimaryKeyFieldFromProps(lookupProps ?? null)
  const selectedField = getLookupSelectedDisplayFieldFromProps(lookupProps ?? null)

  if (selectedField) {
    const selectedVal = lookupCellValue(row, selectedField)
    if (selectedVal != null) {
      return unwrapUserLikeValueToDisplayString(selectedVal)
    }
  }

  const displayFields = [
    ...(Array.isArray(lookupProps?.displayFields) ? lookupProps!.displayFields! : []),
    ...(Array.isArray(cfg.displayFields) ? cfg.displayFields : []),
  ]
  for (const field of displayFields) {
    if (!field || field === pkField) continue
    const val = lookupCellValue(row, String(field))
    if (val != null) {
      return unwrapUserLikeValueToDisplayString(val)
    }
  }

  const displayField = pickFirstNonPrimaryDisplayField(pkField, [
    lookupProps?.displayField,
    cfg.displayField,
  ])
  if (displayField) {
    const val = lookupCellValue(row, displayField)
    if (val != null) {
      return unwrapUserLikeValueToDisplayString(val)
    }
  }

  // Only show PK when designer explicitly configured it as the display field.
  if (selectedField === pkField || cfg.selectedDisplayField === pkField) {
    const pkVal = lookupCellValue(row, pkField) ?? lookupCellValue(row, 'id')
    if (pkVal != null) {
      return unwrapUserLikeValueToDisplayString(pkVal)
    }
  }

  return '-'
}

/** Merge subForm rule lookupConfig onto derived columns (list-view merges may drop selectedDisplayField). */
export function enrichLookupColumnPropsFromSubFormRule(
  columns: DialogColumn[],
  subFormRule?: unknown[] | null,
): DialogColumn[] {
  if (!Array.isArray(subFormRule) || subFormRule.length === 0) return columns
  const ruleByField = new Map<string, { type?: string; props?: Record<string, unknown> }>()
  for (const item of subFormRule) {
    if (!item || typeof item !== 'object') continue
    const field = String((item as { field?: string }).field || '').trim()
    if (field) ruleByField.set(field, item as { type?: string; props?: Record<string, unknown> })
  }
  return columns.map(col => {
    const rule = ruleByField.get(col.field)
    const rawCfg = rule?.props?.lookupConfig ?? col.props?.lookupConfig
    if (!rawCfg && col.type !== 'lookup' && rule?.type !== 'lookup') return col
    const lookupProps = buildLookupColumnProps(rawCfg || '{}')
    return {
      ...col,
      type: col.type === 'lookup' || rule?.type === 'lookup' ? 'lookup' : col.type,
      props: { ...(col.props || {}), ...lookupProps },
    }
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
    if (typeof rawValue === 'object' && rawValue != null && !Array.isArray(rawValue)) {
      return resolveLookupCellTagText(col.props ?? null, rawValue as Record<string, unknown>)
    }
    if (typeof rawValue === 'string' || typeof rawValue === 'number') {
      const pk = getLookupPrimaryKeyFieldFromProps(col.props ?? null)
      const synthetic = { [pk]: rawValue } as Record<string, unknown>
      const label = resolveLookupCellTagText(col.props ?? null, synthetic)
      return label !== '-' ? label : String(rawValue)
    }
    return '-'
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
