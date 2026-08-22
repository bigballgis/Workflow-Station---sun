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
