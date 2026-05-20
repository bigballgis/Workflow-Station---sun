import axios from 'axios'
import { getUser } from './auth'

// Create axios instance for sub-table view API
const subTableViewAxios = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  withCredentials: true
})

subTableViewAxios.interceptors.request.use(config => {
  const user = getUser()
  if (user && user.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  return config
})

subTableViewAxios.interceptors.response.use(
  response => {
    if (response.data && typeof response.data === 'object' && 'success' in response.data) {
      if (response.data.success === false) {
        const error = new Error(response.data.error?.message || 'Request failed')
        ;(error as any).response = { status: 500, data: response.data }
        return Promise.reject(error)
      }
    }
    return response.data
  },
  error => Promise.reject(error)
)

// ==================== 类型定义 ====================

export interface SubTableFieldDTO {
  fieldName: string
  dataType: string
  length?: number
  precision?: number
  scale?: number
  nullable: boolean
  isPrimaryKey: boolean
  defaultValue?: string
  comment?: string
}

export interface SubTableViewFieldDTO {
  fieldName: string
  displayLabel: string
  columnWidth?: number
  sortOrder: number
  visible?: boolean
}

export interface SubTableViewConfig {
  id: number
  bindingId: number
  tableId: number
  viewFields: SubTableViewFieldDTO[]
}

// ==================== API ====================

export const subTableViewApi = {
  /** 获取 View 配置 */
  getViewConfig: (formId: number, bindingId: number) =>
    subTableViewAxios.get<any, { data: SubTableViewConfig }>(
      `/api/forms/${formId}/sub-table-views/${bindingId}`
    ),

  /** 保存 View 字段配置 */
  saveViewConfig: (formId: number, bindingId: number, fields: SubTableViewFieldDTO[]) =>
    subTableViewAxios.put<any, { data: SubTableViewConfig }>(
      `/api/forms/${formId}/sub-table-views/${bindingId}`,
      fields
    ),

  /** 获取可用字段列表 */
  getAvailableFields: (formId: number, bindingId: number) =>
    subTableViewAxios.get<any, { data: SubTableFieldDTO[] }>(
      `/api/forms/${formId}/sub-table-views/${bindingId}/fields`
    ),

  /** 创建默认视图（当绑定子表时自动调用） */
  createDefaultView: (formId: number, bindingId: number) =>
    subTableViewAxios.post<any, { data: SubTableViewConfig }>(
      `/api/forms/${formId}/sub-table-views/${bindingId}/default`
    ),

  /** 获取或创建视图配置 */
  getOrCreateView: (formId: number, bindingId: number) =>
    subTableViewAxios.get<any, { data: SubTableViewConfig }>(
      `/api/forms/${formId}/sub-table-views/${bindingId}/or-create`
    ),
}
