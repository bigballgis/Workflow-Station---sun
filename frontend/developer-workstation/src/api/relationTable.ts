import axios from 'axios'
import i18n from '@/i18n'
import { getUser } from './auth'

// Create axios instance for relation table API
const relationTableAxios = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  withCredentials: true
})

relationTableAxios.interceptors.request.use(config => {
  // This UI is English-only; without the header the backend answers in the browser's language.
  config.headers['Accept-Language'] = i18n.global.locale.value
  const user = getUser()
  if (user && user.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  return config
})

relationTableAxios.interceptors.response.use(
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

export interface RelationTableDTO {
  id: number
  tableName: string
  displayName: string
  description?: string
  status: string
  enabled: boolean
  portalVisible: boolean
  currentVersion: number
  fieldDefinitions?: RelationFieldDTO[]
}

export interface RelationTableBindingDTO {
  bindingId: number
  tableId: number
  tableName: string
  displayName: string
  bindingType: string
  viewConfigId: number | null
}

export interface ViewFieldDTO {
  fieldName: string
  displayLabel: string
  columnWidth: number
  sortOrder: number
  visible: boolean
}

export interface RelationViewConfig {
  id: number
  bindingId: number
  tableId: number
  fieldConfig?: string
  viewFields: ViewFieldDTO[]
}

export interface RelationFieldDTO {
  id: number
  fieldName: string
  dataType: string
  length?: number
  precision?: number
  scale?: number
  nullable: boolean
  isPrimaryKey: boolean
  defaultValue?: string
  displayName?: string
  sortOrder: number
}

export interface LookupConfigDTO {
  viewConfigId: number | null
  tableId: number
  searchFields: string
  displayField: string
}

export interface RelationLookupConfig {
  id: number
  formId: number
  componentId: string
  viewConfigId: number | null
  tableId: number
  searchFields: string
  displayField: string
}

export interface BoundViewDTO {
  bindingId: number
  tableId: number
  tableName: string
  displayName: string
  viewConfigId: number | null
}

// ==================== Binding API ====================

export const relationTableBindingApi = {
  /** 获取可绑定的 Relation Table 列表 */
  getAvailableTables: () =>
    relationTableAxios.get<any, { data: RelationTableDTO[] }>('/api/relation-tables/available'),

  /** 绑定 Relation Table */
  bind: (formId: number, tableId: number) =>
    relationTableAxios.post<any, { data: { bindingId: number } }>(
      `/api/forms/${formId}/relation-bindings`,
      { tableId }
    ),

  /** 解除绑定 */
  unbind: (formId: number, bindingId: number) =>
    relationTableAxios.delete(`/api/forms/${formId}/relation-bindings/${bindingId}`),

  /** 获取绑定列表 */
  getBindings: (formId: number) =>
    relationTableAxios.get<any, { data: RelationTableBindingDTO[] }>(
      `/api/forms/${formId}/relation-bindings`
    ),
}

// ==================== View API ====================

export const relationTableViewApi = {
  /** 获取 View 配置 */
  getViewConfig: (formId: number, bindingId: number) =>
    relationTableAxios.get<any, { data: RelationViewConfig }>(
      `/api/forms/${formId}/relation-views/${bindingId}`
    ),

  /** 保存 View 字段配置 */
  saveViewConfig: (formId: number, bindingId: number, fields: ViewFieldDTO[]) =>
    relationTableAxios.put<any, { data: RelationViewConfig }>(
      `/api/forms/${formId}/relation-views/${bindingId}`,
      fields
    ),

  /** 获取可用字段列表 */
  getAvailableFields: (formId: number, bindingId: number) =>
    relationTableAxios.get<any, { data: RelationFieldDTO[] }>(
      `/api/forms/${formId}/relation-views/${bindingId}/fields`
    ),

  /** 通过 tableId 直接获取可用字段列表 */
  getFieldsByTableId: (formId: number, tableId: number) =>
    relationTableAxios.get<any, { data: RelationFieldDTO[] }>(
      `/api/forms/${formId}/relation-views/fields-by-table?tableId=${tableId}`
    ),
}

// ==================== Lookup API ====================

export const relationTableLookupApi = {
  /** 获取 Lookup 配置 */
  getLookupConfig: (formId: number, componentId: string) =>
    relationTableAxios.get<any, { data: RelationLookupConfig | null }>(
      `/api/forms/${formId}/lookup-config/${componentId}`
    ),

  /** 保存 Lookup 配置 */
  saveLookupConfig: (formId: number, componentId: string, config: LookupConfigDTO) =>
    relationTableAxios.put<any, { data: RelationLookupConfig }>(
      `/api/forms/${formId}/lookup-config/${componentId}`,
      config
    ),

  /** 获取已绑定的 View 列表 */
  getBoundViews: (formId: number, componentId: string) =>
    relationTableAxios.get<any, { data: BoundViewDTO[] }>(
      `/api/forms/${formId}/lookup-config/${componentId}/bound-views`
    ),
}
