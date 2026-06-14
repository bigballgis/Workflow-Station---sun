import type { SubTableFieldDTO } from '@/api/subTableView'

export interface LinkFormComponentInfo {
  id: number
  componentName: string
  linkedFormId: number
  linkedFormName?: string
  displayField?: string
  linkText?: string
  columnLabel?: string
}

export interface SubTableBindingOption {
  bindingId: number
  tableName: string
  /** Table Display Name 来自 dw_table_definitions.tableDisplayName，渲染优先于 tableName。 */
  tableDisplayName?: string
  tableId?: number
  tableDescription?: string
}

export interface SubTableFormDesign {
  rule: any[]
  options?: Record<string, unknown>
}

export interface LookupPreviewConfig {
  placeholder: string
  searchFields: string[]
  displayFields: string[]
  selectedDisplayField: string
  filterConditions: any[]
  viewFields: any[]
  fieldDefs: any[]
  showBackfillView: boolean
}

export interface SubTableListColumnDTO extends SubTableFieldDTO {
  columnType?: 'field' | 'linkForm' | 'lookup'
  componentId?: number
  linkedFormId?: number
  linkedFormName?: string
  linkText?: string
  columnLabel?: string
  boundSubTableBindingId?: number
  boundSubTableName?: string
  lookupConfig?: string
}

export interface SubTableListViewProps {
  binding: {
    bindingId: number
    bindingType: string
    bindingMode: string
    tableName: string
    tableDisplayName?: string
    tableId: number
    tableType: string
    tableDescription: string
  }
  functionUnitId: number
  formId: number
  /** All available fields for this sub-table */
  availableFields?: SubTableFieldDTO[]
  /** Fields currently shown in the view (ordered) */
  modelValue?: SubTableListColumnDTO[]
  linkFormComponents?: LinkFormComponentInfo[]
  subTableBindings?: SubTableBindingOption[]
  resolveSubTableFormDesign?: (bindingId: number) => SubTableFormDesign
  resolveLookupPreviewConfig?: (lookupConfig: string) => LookupPreviewConfig
  /** Sub-table form design rendered when a Link Form column is clicked */
  formRule?: any[]
  formOption?: Record<string, unknown>
  /**
   * Binding-level portal display (User Portal To Do / My Requests). When To Do is
   * "form below table" and My Requests is not "same as To Do", the list preview shows
   * two panes and the preview dialog uses two tabs.
   */
  portalViews?: {
    assigneeTodo: 'formBelowTable' | 'tableOnly'
    initiatorRequest: 'mirrorTodo' | 'summaryWithLinkFormModal' | 'tableOnly'
  } | null
}

export interface SubTableListViewEmit {
  (e: 'update:modelValue', fields: SubTableListColumnDTO[]): void
  (e: 'update:availableFields', fields: SubTableFieldDTO[]): void
  (e: 'save'): void
}

export type TFn = (key: string, params?: Record<string, unknown>) => string
