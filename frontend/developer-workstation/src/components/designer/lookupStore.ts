import { reactive } from 'vue'

interface RelationBinding {
  bindingId: number
  tableName: string
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

/** Shared reactive state between FormDesigner and LookupBindingSelect */
export const lookupStore = reactive({
  formId: null as number | null,
  relationBindings: [] as RelationBinding[],
  tables: [] as TableDef[],
  /** Cache of relation table fields keyed by tableId (for deployed rt_table_definitions) */
  rtFieldCache: {} as Record<number, FieldDef[]>,
  /**
   * Navigation callback set by FormDesigner so property-panel components registered in
   * fc-designer's separate Vue app (where provide/inject doesn't reach) can still switch tabs.
   */
  switchToBinding: null as ((id: number) => void) | null,
})
