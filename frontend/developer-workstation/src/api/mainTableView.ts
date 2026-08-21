import { functionUnitAxios } from './functionUnit'

export type MainTableViewColumnType = 'field' | 'lookup_display' | 'fk_display'

export interface MainTableViewField {
  fieldName: string
  displayLabel?: string | null
  columnWidth?: number | null
  sortOrder?: number | null
  visible?: boolean | null
  systemField?: boolean | null
  // Derived from FieldDefinition (output-only); drives FK/PK column styling + designer FK navigation.
  isPrimaryKey?: boolean | null
  isForeignKey?: boolean | null
  refTableId?: number | null
  refPrimaryKeyFields?: string[] | null
  /** Physical/system column vs lookup_display / fk_display related attributes. */
  columnType?: MainTableViewColumnType | null
  /** For lookup_display / fk_display: source field (lookup widget or FK column). */
  lookupSourceField?: string | null
  /** For lookup_display / fk_display: attribute on the target row. */
  lookupDisplayField?: string | null
}

export interface MainTableLookupCatalogGroup {
  sourceField: string
  sourceLabel: string
  tableId: number
  tableName: string
  fields: MainTableFieldCatalogItem[]
  /** lookup = form lookup widget; fk = table-design foreign key. */
  relationKind?: 'lookup' | 'fk'
}

/** Synthetic view field name for a lookup-derived attribute column. */
export function lookupDisplayFieldName(sourceField: string, displayField: string): string {
  return `${sourceField}@${displayField}`
}

export interface FilterCondition {
  fieldName: string
  operator: string
  value?: string | null
  systemField?: boolean
}

export interface FilterGroup {
  logic: 'and' | 'or'
  conditions?: FilterCondition[]
  groups?: FilterGroup[]
}

export interface FilterConfig {
  logic?: 'and' | 'or'
  conditions?: FilterCondition[]
  groups?: FilterGroup[]
  toolbar?: {
    enableExport?: boolean
    enableImport?: boolean
  }
}

export interface MainTableViewAccessRule {
  targetType: 'ROLE' | 'BUSINESS_UNIT' | string
  targetId: string
  targetName?: string | null
}

export interface MainTableViewDefinition {
  id: number
  functionUnitId: number
  mainTableId: number
  viewName: string
  isDefault?: boolean
  status?: string
  restrictToInvolvedUsers?: boolean
  /** DETAIL form opened when a portal user clicks a row; null = rows not clickable. */
  detailFormId?: number | null
  accessRules?: MainTableViewAccessRule[]
  sortConfig?: Array<{ fieldName: string; direction: string; systemField?: boolean }>
  filterConfig?: FilterConfig
  fields: MainTableViewField[]
}

export interface MainTableFieldCatalogItem {
  fieldName: string
  displayName?: string
  dataType?: string
  systemField?: boolean
  /** Present when this catalog entry is a lookup-derived attribute. */
  columnType?: MainTableViewColumnType
  lookupSourceField?: string
  lookupDisplayField?: string
  lookupTableId?: number
  lookupTableName?: string
}

export const SYSTEM_VIEW_FIELDS: MainTableFieldCatalogItem[] = [
  { fieldName: 'process_status', displayName: 'Status', dataType: 'VARCHAR', systemField: true },
  { fieldName: 'start_time', displayName: 'Start Time', dataType: 'TIMESTAMP', systemField: true },
  { fieldName: 'initiator', displayName: 'Initiator', dataType: 'VARCHAR', systemField: true },
  { fieldName: 'current_step', displayName: 'Current Step', dataType: 'VARCHAR', systemField: true },
]

export const mainTableViewApi = {
  list: (functionUnitId: number) =>
    functionUnitAxios.get<any, { data: MainTableViewDefinition[] }>(
      `/api/v1/function-units/${functionUnitId}/main-table-views`,
    ),

  get: (functionUnitId: number, viewId: number) =>
    functionUnitAxios.get<any, { data: MainTableViewDefinition }>(
      `/api/v1/function-units/${functionUnitId}/main-table-views/${viewId}`,
    ),

  create: (functionUnitId: number, viewName: string, tableId: number) =>
    functionUnitAxios.post<any, { data: MainTableViewDefinition }>(
      `/api/v1/function-units/${functionUnitId}/main-table-views`,
      { viewName, tableId },
    ),

  update: (functionUnitId: number, viewId: number, payload: Partial<MainTableViewDefinition>) =>
    functionUnitAxios.put<any, { data: MainTableViewDefinition }>(
      `/api/v1/function-units/${functionUnitId}/main-table-views/${viewId}`,
      {
        viewName: payload.viewName,
        restrictToInvolvedUsers: payload.restrictToInvolvedUsers,
        detailFormId: payload.detailFormId ?? null,
        accessRules: payload.accessRules,
        sortConfig: payload.sortConfig,
        filterConfig: payload.filterConfig,
        fields: payload.fields,
      },
    ),

  /**
   * Sets only the detail form. Prefer this over `update` when that is all that changed: `update`
   * is a whole-design save that resets the view to DRAFT, which would hide a published view from
   * the portal as a side effect.
   */
  updateDetailForm: (functionUnitId: number, viewId: number, detailFormId: number | null) =>
    functionUnitAxios.patch<any, { data: MainTableViewDefinition }>(
      `/api/v1/function-units/${functionUnitId}/main-table-views/${viewId}/detail-form`,
      { detailFormId },
    ),

  delete: (functionUnitId: number, viewId: number) =>
    functionUnitAxios.delete(`/api/v1/function-units/${functionUnitId}/main-table-views/${viewId}`),

  // Generate a default view for every MAIN/SUB table that has none (for legacy function units).
  seedDefaults: (functionUnitId: number) =>
    functionUnitAxios.post<any, { data: MainTableViewDefinition[] }>(
      `/api/v1/function-units/${functionUnitId}/main-table-views/seed-defaults`,
    ),
}
