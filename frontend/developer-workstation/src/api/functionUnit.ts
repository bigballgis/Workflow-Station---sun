import api from './index'

export interface FunctionUnit {
  id: number
  name: string
  description?: string
  iconId?: number
  status: string
  currentVersion?: string
  createdAt: string
  updatedAt: string
  tableCount: number
  formCount: number
  actionCount: number
  hasProcess: boolean
}

export interface FunctionUnitRequest {
  name: string
  description?: string
  iconId?: number
}

export const functionUnitApi = {
  list: (params: { name?: string; status?: string; page?: number; size?: number }) =>
    api.get('/function-units', { params }),
  
  getById: (id: number) => api.get(`/function-units/${id}`),
  
  create: (data: FunctionUnitRequest) => api.post('/function-units', data),
  
  update: (id: number, data: FunctionUnitRequest) => api.put(`/function-units/${id}`, data),
  
  delete: (id: number) => api.delete(`/function-units/${id}`),
  
  publish: (id: number, changeLog?: string) => 
    api.post(`/function-units/${id}/publish`, null, { params: { changeLog } }),
  
  clone: (id: number, newName: string) => 
    api.post(`/function-units/${id}/clone`, null, { params: { newName } }),
  
  validate: (id: number) => api.get(`/function-units/${id}/validate`)
}
