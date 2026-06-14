import { functionUnitAxios } from './functionUnit'

export interface MainTableViewField {
  fieldName: string
  displayLabel?: string | null
  columnWidth?: number | null
  sortOrder?: number | null
  visible?: boolean | null
  systemField?: boolean | null
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

export interface MainTableViewDefinition {
  id: number
  functionUnitId: number
  mainTableId: number
  viewName: string
  isDefault?: boolean
  status?: string
  sortConfig?: Array<{ fieldName: string; direction: string; systemField?: boolean }>
  filterConfig?: FilterConfig
  fields: MainTableViewField[]
}

export interface MainTableFieldCatalogItem {
  fieldName: string
  displayName?: string
  dataType?: string
  systemField?: boolean
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

  create: (functionUnitId: number, viewName: string) =>
    functionUnitAxios.post<any, { data: MainTableViewDefinition }>(
      `/api/v1/function-units/${functionUnitId}/main-table-views`,
      { viewName },
    ),

  update: (functionUnitId: number, viewId: number, payload: Partial<MainTableViewDefinition>) =>
    functionUnitAxios.put<any, { data: MainTableViewDefinition }>(
      `/api/v1/function-units/${functionUnitId}/main-table-views/${viewId}`,
      {
        viewName: payload.viewName,
        sortConfig: payload.sortConfig,
        filterConfig: payload.filterConfig,
        fields: payload.fields,
      },
    ),

  delete: (functionUnitId: number, viewId: number) =>
    functionUnitAxios.delete(`/api/v1/function-units/${functionUnitId}/main-table-views/${viewId}`),
}
