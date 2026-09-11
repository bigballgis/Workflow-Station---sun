import { get, post, put, del } from './request'

export interface BusinessUnit {
  id: string
  name: string
  code: string
  parentId?: string
  parentName?: string
  level: number
  sortOrder: number
  status: 'ACTIVE' | 'INACTIVE'
  memberCount: number
  children?: BusinessUnit[]
  createdAt: string
  updatedAt: string
}

export interface CreateBusinessUnitRequest {
  name: string
  code: string
  parentId?: string
  sortOrder?: number
}

export interface UpdateBusinessUnitRequest {
  name?: string
  sortOrder?: number
}

export interface MoveBusinessUnitRequest {
  newParentId?: string
  sortOrder?: number
}

export const organizationApi = {
  getTree: () => get<BusinessUnit[]>('/business-units/tree'),
  
  getById: (id: string) => get<BusinessUnit>(`/business-units/${id}`),
  
  create: (data: CreateBusinessUnitRequest) => post<BusinessUnit>('/business-units', data),
  
  update: (id: string, data: UpdateBusinessUnitRequest) => put<BusinessUnit>(`/business-units/${id}`, data),
  
  delete: (id: string) => del<void>(`/business-units/${id}`),
  
  // 后端是 PUT /business-units/{id}/move；此前误用 POST 导致拖拽一律 500（Request method POST not supported）
  move: (id: string, data: MoveBusinessUnitRequest) => put<void>(`/business-units/${id}/move`, data),
  
  getMembers: (id: string, params?: { page?: number; size?: number }) => 
    get<any>(`/business-units/${id}/members`, { params }),
  
  getStatistics: (id: string) => get<{ memberCount: number; childCount: number }>(`/business-units/${id}/statistics`)
}
