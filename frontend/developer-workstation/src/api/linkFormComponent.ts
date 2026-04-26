import axios from 'axios'
import { TOKEN_KEY, getUser } from './auth'

const apiAxios = axios.create({
  baseURL: '',
  timeout: 30000
})

apiAxios.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const user = getUser()
  if (user && user.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  return config
})

export interface LinkFormComponentRequest {
  componentName: string
  linkedFormId: number
  displayField?: string
  linkText?: string
  columnLabel?: string
  sortOrder?: number
  configJson?: string
}

export interface LinkFormComponentResponse {
  id: number
  functionUnitId: number
  componentName: string
  linkedFormId: number
  linkedFormName?: string
  displayField?: string
  linkText: string
  columnLabel?: string
  sortOrder: number
  configJson?: string
  createdAt: string
  updatedAt: string
}

export interface LinkFormDataRequest {
  componentId: number
  subTableRowId: number
  formData: Record<string, any> | string
}

export interface LinkFormDataResponse {
  id: number
  componentId: number
  subTableRowId: number
  formData: Record<string, any>
  createdAt: string
  updatedAt: string
}

export const linkFormComponentApi = {
  getComponents: (functionUnitId: number) =>
    apiAxios.get<any, { data: LinkFormComponentResponse[] }>(
      `/api/v1/function-units/${functionUnitId}/link-form-components`
    ),

  getComponent: (functionUnitId: number, id: number) =>
    apiAxios.get<any, { data: LinkFormComponentResponse }>(
      `/api/v1/function-units/${functionUnitId}/link-form-components/${id}`
    ),

  create: (functionUnitId: number, data: LinkFormComponentRequest) =>
    apiAxios.post<any, { data: LinkFormComponentResponse }>(
      `/api/v1/function-units/${functionUnitId}/link-form-components`,
      data
    ),

  update: (functionUnitId: number, id: number, data: LinkFormComponentRequest) =>
    apiAxios.put<any, { data: LinkFormComponentResponse }>(
      `/api/v1/function-units/${functionUnitId}/link-form-components/${id}`,
      data
    ),

  delete: (functionUnitId: number, id: number) =>
    apiAxios.delete(`/api/v1/function-units/${functionUnitId}/link-form-components/${id}`),

  saveFormData: (functionUnitId: number, data: LinkFormDataRequest) =>
    apiAxios.post<any, { data: LinkFormDataResponse }>(
      `/api/v1/function-units/${functionUnitId}/link-form-components/data`,
      data
    ),

  getFormData: (functionUnitId: number, componentId: number, subTableRowId: number) =>
    apiAxios.get<any, { data: LinkFormDataResponse }>(
      `/api/v1/function-units/${functionUnitId}/link-form-components/data`,
      { params: { componentId, subTableRowId } }
    ),
}
