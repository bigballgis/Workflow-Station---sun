import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

// 列配置接口
export interface ColumnConfig {
  field: string
  label: string
  type?: 'input' | 'number' | 'date' | 'switch' | 'text' | 'textarea' | 'select' | 'radio' | 'checkbox' | 'datetime' | 'upload' | 'user' | 'department' | 'password' | 'timerange' | 'treeselect' | 'colorPicker' | 'rate' | 'slider' | 'tree' | 'editor' | 'signature' | 'transfer' | 'cascader' | 'linkForm' | 'lookup' | 'owner'
  width?: number
  minWidth?: number
  required?: boolean
  /** Element Plus rules from Form Design validate (simple dialog path). */
  rules?: Array<Record<string, unknown>>
  placeholder?: string
  options?: Array<{ label: string; value: any }>
  props?: Record<string, any>
  /** Form-create rule node — on/_on/hook/_hook for Element Plus dialog events. */
  sourceRule?: Record<string, unknown>
}

// 子表配置接口
export interface SubTableConfig {
  title?: string
  bindingId?: number
  tableId?: number
  columns: ColumnConfig[]
  fieldDefinitions?: BindingFieldDefinition[]
  bindingLinkMode?: 'structuralFk' | 'miParticipantRow' | string
  bindingForeignKeyField?: string | null
  /**
   * ACTION 绑定（FORM_POPUP 弹窗写入的记录表）语义上是操作留痕，恒只读——SubTableField 据此
   * 强制 editable=false，不依赖 allowAdd/allowEdit/allowDelete props 的值。
   */
  bindingType?: string
  pagination?: boolean
  pageSize?: number
  maxHeight?: number
}

export interface SubTableFieldProps {
  config: SubTableConfig
  modelValue?: any[]
  editable?: boolean
  foreignKeyValue?: string | number
  /** Form-create rule from the sub-table form designer */
  formRule?: any[]
  /** Form-create option from the sub-table form designer */
  formOption?: any
  /** Form Preview: compact lookup cells (My Requests — summary mode) */
  previewLookupCompact?: boolean
  /** Form Preview: main form data for FK fill */
  primaryFormData?: Record<string, unknown>
  functionUnitId?: number
  primaryTableDisplayName?: string
  primaryTableId?: number | null
  parentTablesById?: Record<number, { fieldDefinitions: BindingFieldDefinition[] }>
  previewTableBindings?: Array<{ tableId?: number | null; bindingType?: string }>
  assignmentConfig?: AssignmentConfig
}

export interface SubTableFieldEmit {
  (e: 'update:modelValue', value: any[]): void
  (e: 'update:primaryFormData', value: Record<string, unknown>): void
  (e: 'add', row: any): void
  (e: 'edit', row: any, index: number): void
  (e: 'delete', row: any, index: number): void
}
