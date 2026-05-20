import axios from 'axios'
import { getUser } from './auth'

const apiAxios = axios.create({
  baseURL: '',
  timeout: 30000,
  withCredentials: true
})

apiAxios.interceptors.request.use(config => {
  const user = getUser()
  if (user && user.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  return config
})

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
