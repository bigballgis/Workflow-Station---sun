import type { AxiosRequestConfig } from 'axios'
import type { ListColumnFilterRequest, ListColumnKind } from '@platform-shared/list/columnMeta'
import { request } from './request'

export type ImportProgressPhase = 'upload' | 'process'
export type ImportProgressCallback = (percent: number, phase: ImportProgressPhase) => void

export interface FunctionUnitViewMenuItem {
  functionUnitId: string
  functionUnitCode: string
  functionUnitName: string
  viewCount: number
  /** Inline SVG markup from DW icon library; null/absent when none. */
  iconSvg?: string | null
}

export interface MainTableViewSummary {
  id: number
  viewName: string
  isDefault?: boolean
  tableId?: number | null
  tableLabel?: string | null
  /** Owning table type; 'MAIN' means a row is a request and opens the request detail page. */
  tableType?: string | null
  enableExport?: boolean
  enableImport?: boolean
  /** DETAIL form opened when a row is clicked; null = rows are not clickable. */
  detailFormId?: number | null
}

export type MainTableViewColumnType = 'field' | 'lookup_display' | 'fk_display'

export interface MainTableViewFieldColumn {
  fieldName: string
  displayLabel: string
  columnWidth?: number
  systemField?: boolean
  // FK drill-down: link to the referenced table's published default view, filtered by this cell's value.
  isForeignKey?: boolean
  refViewId?: number | null
  refFunctionUnitCode?: string | null
  refPrimaryKeyFields?: string[] | null
  // Lookup drill-down: link to the referenced Relation Table's data, filtered by this cell's value.
  isLookup?: boolean
  lookupTableId?: number | null
  columnType?: MainTableViewColumnType | null
  lookupSourceField?: string | null
  lookupDisplayField?: string | null
  lookupSelectedDisplayField?: string | null
  lookupSearchFields?: string[] | null
  /** For fk_display: DW table id of the FK target (e.g. Case table). */
  fkRefTableId?: number | null
  // What the header may offer on this column. Declared by the backend, which is the only side
  // that knows whether the query can answer that question — never inferred here.
  kind: ListColumnKind
  filterable: boolean
  sortable: boolean
  operators: string[]
  /** Closed choices for ENUM / BOOLEAN; omitted or empty for open-value kinds. */
  options?: { value: string; label: string }[]
}

/** Mirrors MainTableViewQueryRequest: paging plus everything the shared header produces. */
export interface MainTableViewQueryRequest {
  page: number
  size: number
  search?: string | null
  /** Exact list row identity; view-detail uses this instead of {@link search}. */
  rowKey?: string | null
  filters?: ListColumnFilterRequest[]
  sortField?: string | null
  sortDirection?: 'ASC' | 'DESC' | null
}

export interface MainTableViewDataRow {
  /**
   * What makes this row distinct from every other row of the view. Equal to the process instance
   * on a MAIN view; on a SUB view one instance contributes many rows, so the instance id alone
   * would repeat.
   */
  rowKey: string
  processInstanceId: string
  values: Record<string, unknown>
}

export interface MainTableViewDataPage {
  columns: MainTableViewFieldColumn[]
  rows: MainTableViewDataRow[]
  total: number
  page: number
  size: number
}

export interface MainTableViewImportResult {
  createdCount: number
  updatedCount: number
  skippedCount: number
  errorCount: number
  errors: string[]
}

export const mainTableViewApi = {
  listFunctionUnits: () =>
    request.get<{ data: FunctionUnitViewMenuItem[] }>('/main-table-views/function-units'),

  listViews: (functionUnitCode: string) =>
    request.get<{ data: MainTableViewSummary[] }>(
      `/main-table-views/function-units/${encodeURIComponent(functionUnitCode)}/views`,
    ),

  queryData: (viewId: number, body: MainTableViewQueryRequest) =>
    request.post<{ data: MainTableViewDataPage }>(`/main-table-views/${viewId}/data`, body),

  /** Exports what the same query would list — paging aside — so the CSV matches the screen. */
  exportCsv: (viewId: number, body: MainTableViewQueryRequest, maxRows = 10000) =>
    request.post<Blob>(`/main-table-views/${viewId}/export`, body, {
      params: { maxRows },
      responseType: 'blob',
    }),

  importCsv: (viewId: number, file: File, onProgress?: ImportProgressCallback) => {
    const formData = new FormData()
    formData.append('file', file)
    let uploadFinished = false
    return request.post<{ data: MainTableViewImportResult }>(
      `/main-table-views/${viewId}/import`,
      formData,
      {
        headers: { 'Content-Type': undefined },
        skipGlobalErrorHandler: true,
        onUploadProgress: progressEvent => {
          if (!onProgress) return
          const total = progressEvent.total ?? 0
          if (total > 0) {
            const ratio = progressEvent.loaded / total
            onProgress(Math.min(50, Math.round(ratio * 50)), 'upload')
            if (progressEvent.loaded >= total && !uploadFinished) {
              uploadFinished = true
              onProgress(52, 'process')
            }
          } else if (!uploadFinished) {
            onProgress(15, 'upload')
          }
        },
      } as AxiosRequestConfig & { skipGlobalErrorHandler?: boolean },
    )
  },
}
