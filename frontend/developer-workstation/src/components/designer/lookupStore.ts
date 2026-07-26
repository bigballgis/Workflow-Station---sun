import { reactive } from 'vue'

interface RelationBinding {
  bindingId: number
  /** Technical / physical table name (e.g. test_2). */
  tableName: string
  /** Human-readable label from rt_table_definitions.display_name when available. */
  tableDisplayName?: string
  tableDescription: string
  tableId: number
}

interface FieldDef {
  id: number
  fieldName: string
  dataType: string
  length?: number
  precision?: number
  scale?: number
  nullable: boolean
  isPrimaryKey: boolean
  defaultValue?: string
  description?: string
  sortOrder: number
}

interface TableDef {
  id: number
  tableName: string
  tableType: string
  description?: string
  fieldDefinitions?: FieldDef[]
  fields?: FieldDef[]
}

export interface SiblingLookupField {
  field: string
  title: string
  tableId: number | null
  tableName: string
  bindingId: number | null
  lookupConfig: Record<string, unknown>
}

/** Shared reactive state between FormDesigner and LookupBindingSelect */
export const lookupStore = reactive({
  formId: null as number | null,
  relationBindings: [] as RelationBinding[],
  tables: [] as TableDef[],
  /** Cache of relation table fields keyed by tableId (for deployed rt_table_definitions) */
  rtFieldCache: {} as Record<number, FieldDef[]>,
  /** Lookup fields in the active designer rule tree (main or sub-form). */
  siblingLookupFields: [] as SiblingLookupField[],
  /** form-create rule.field of the lookup currently open in the property panel. */
  editingLookupField: null as string | null,
  /** Refreshes siblingLookupFields from the active fc-designer canvas. */
  refreshSiblingLookups: null as (() => void) | null,
  /**
   * Navigation callback set by FormDesigner so property-panel components registered in
   * fc-designer's separate Vue app (where provide/inject doesn't reach) can still switch tabs.
   */
  switchToBinding: null as ((id: number) => void) | null,
})
