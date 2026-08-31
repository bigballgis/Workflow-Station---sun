export type ColumnType =
  | 'text'
  // form-create 画布里文本控件的原始类型名。FieldRenderer / displayValue 都把它
  // 与 'text' 同等对待（见 FieldRenderer.vue 的 `type === 'text' || === 'input'`），
  // 但此前没进 union，于是那些比较被 TS 判为「无重叠」。是 union 不全，不是比较多余。
  | 'input'
  // Link Form 列。运行时长期存在（SubTableField 靠它选渲染分支、
  // formRendererSubTableBindings 靠它收集绑定 id），同样漏在 union 外。
  | 'linkForm'
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
  | 'owner'

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
  /**
   * Designer "Hide" toggle from the sub-form canvas rule (`hidden` / `_hidden` /
   * `display: false` / `props.hidden`). Seeds the dialog's script-visibility state on open,
   * so a statically hidden field starts hidden — an event script may still reveal it via
   * `api.hidden(false, …)`. Without this the flag was dropped and the field always showed.
   */
  hidden?: boolean
  /** Element Plus rules from Form Design `rule.validate`; preferred by buildRules over required-only. */
  rules?: Array<Record<string, unknown>>
  /** Form Design Basis / rule default; used by buildInitialRow for Add dialog. */
  defaultValue?: unknown
  /** Form-create rule node — carries on/_on/hook/_hook for dialog component events. */
  sourceRule?: Record<string, unknown>
  placeholder?: string
  minWidth?: number
  options?: Array<{ label: string; value: string | number }>
  props?: {
    action?: string
    accept?: string
    fileNameTargetField?: string
    cannotDownload?: boolean
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
    // 以下几个 SubTableAddDialog 模板里实际在用，但此前没声明，于是落到下面的
    // `[key: string]: unknown` 上 —— 绑到 el-* 的具名 prop 时就成了 unknown/{}。
    // 补声明即可，运行时读的还是同一个字段。
    showAlpha?: boolean
    showCheckbox?: boolean
    showBackfillView?: boolean
    allowHalf?: boolean
    step?: number
    leftTitle?: string
    rightTitle?: string
    cascaderProps?: Record<string, unknown>
    [key: string]: unknown
  }
}

export interface UserSnapshotViewField {
  key: string
  label: string
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

export type SubListViewColumn = {
  fieldName?: string
  columnType?: string
  dataType?: string
  [key: string]: unknown
}

import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'

export type ParsedLookupConfig = {
  bindingId?: number
  tableId?: number | null
  tableName?: string
  searchFields?: string[]
  displayFields?: string[]
  selectedDisplayField?: string
  displayField?: string
  filterConditions?: LookupFilterCondition[]
  showBackfillView?: boolean
}
