import type { ListColumnFilterRequest, ListColumnKind } from '@platform-shared/list/columnMeta'
import { request } from './request'

export interface FunctionUnitViewMenuItem {
  functionUnitId: string
  functionUnitCode: string
  functionUnitName: string
  viewCount: number
  /** Inline SVG markup from the Admin deployed release; null/absent when none. */
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
  /** The link points at the request this SUB row lives in: open that request, not a filtered list. */
  refOwningRequest?: boolean
  // Lookup drill-down: link to the referenced Relation Table's data, filtered by this cell's value.
  isLookup?: boolean
  lookupTableId?: number | null
  columnType?: MainTableViewColumnType | null
  lookupSourceField?: string | null
  lookupDisplayField?: string | null
  lookupSelectedDisplayField?: string | null
  lookupSearchFields?: string[] | null
  /** For fk_display: Published table reference of the FK target (e.g. Case table). */
  fkRefTableId?: number | null
  // What the header may offer on this column. Declared by the backend, which is the only side
  // that knows whether the query can answer that question — never inferred here.
  kind: ListColumnKind
  filterable: boolean
  sortable: boolean
  operators: string[]
  /** Closed choices for ENUM / BOOLEAN; omitted or empty for open-value kinds. */
  options?: { value: string; label: string }[]
  /** What a select-like column shows: the stored option value (default) or the option label. */
  selectDisplay?: 'value' | 'label' | null
  /** Static options of the bound form widget; sent only when {@link selectDisplay} is 'label'. */
  selectOptions?: { value: string; label: string }[] | null
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
  /** Whether this SUB row has a DETAIL definition in its pinned release. */
  detailAvailable?: boolean | null
  detailForm?: Record<string, unknown> | null
  functionUnitVersionLabel?: string | null
  values: Record<string, unknown>
}

export interface MainTableViewDataPage {
  columns: MainTableViewFieldColumn[]
  rows: MainTableViewDataRow[]
  total: number
  page: number
  size: number
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

}
