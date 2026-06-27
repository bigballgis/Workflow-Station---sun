import type { AxiosRequestConfig } from 'axios'
import { request } from './request'

export type ImportProgressPhase = 'upload' | 'process'
export type ImportProgressCallback = (percent: number, phase: ImportProgressPhase) => void

export interface FunctionUnitViewMenuItem {
  functionUnitId: string
  functionUnitCode: string
  functionUnitName: string
  viewCount: number
}

export interface MainTableViewSummary {
  id: number
  viewName: string
  isDefault?: boolean
  enableExport?: boolean
  enableImport?: boolean
}

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
}

export interface MainTableViewDataRow {
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

  queryData: (viewId: number, params: { page?: number; size?: number; search?: string }) =>
    request.get<{ data: MainTableViewDataPage }>(`/main-table-views/${viewId}/data`, { params }),

  exportCsv: (viewId: number, maxRows = 10000) =>
    request.get<Blob>(`/main-table-views/${viewId}/export`, {
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
