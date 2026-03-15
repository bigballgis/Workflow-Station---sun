import request from './request'

export interface CommonFieldDef {
  id: number
  fieldName: string
  displayName?: string
  dataType: string
  isPrimaryKey?: boolean
  nullable?: boolean
  sortOrder?: number
}

export interface CommonTableDef {
  id: number
  code: string
  name: string
  description?: string
  status: string
  fieldDefinitions: CommonFieldDef[]
}

export interface CommonTableDataRow {
  id: number
  commonTableId: number
  dataJson: Record<string, any>
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
  fields: CommonFieldDef[]
}

const BASE = '/common-table-data'

export const commonTableApi = {
  listTables(): Promise<{ success: boolean; data: CommonTableDef[] }> {
    return request.get(`${BASE}/tables`)
  },

  getTable(code: string): Promise<{ success: boolean; data: CommonTableDef }> {
    return request.get(`${BASE}/tables/${code}`)
  },

  listData(tableCode: string, page = 0, size = 20): Promise<{ success: boolean; data: PageResult<CommonTableDataRow> }> {
    return request.get(`${BASE}/${tableCode}`, { params: { page, size } })
  },

  getRowById(tableCode: string, rowId: number): Promise<{ success: boolean; data: CommonTableDataRow }> {
    return request.get(`${BASE}/${tableCode}/data/${rowId}`)
  },

  search(tableCode: string, keyword?: string, displayField?: string): Promise<{ success: boolean; data: CommonTableDataRow[] }> {
    const params: Record<string, string> = {}
    if (keyword) params.keyword = keyword
    if (displayField) params.displayField = displayField
    return request.get(`${BASE}/${tableCode}/search`, { params })
  },

  create(tableCode: string, dataJson: Record<string, any>): Promise<{ success: boolean; data: CommonTableDataRow }> {
    return request.post(`${BASE}/${tableCode}`, dataJson)
  },

  update(tableCode: string, id: number, dataJson: Record<string, any>): Promise<{ success: boolean; data: CommonTableDataRow }> {
    return request.put(`${BASE}/${tableCode}/${id}`, dataJson)
  },

  delete(tableCode: string, id: number): Promise<{ success: boolean }> {
    return request.delete(`${BASE}/${tableCode}/${id}`)
  },

  getExportUrl(tableCode: string): string {
    return `/api/portal${BASE}/${tableCode}/export`
  }
}
