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
  getTree: () => get<BusinessUnit[]>('/departments/tree'),
  
  getById: (id: string) => get<BusinessUnit>(`/departments/${id}`),
  
  create: (data: CreateBusinessUnitRequest) => post<BusinessUnit>('/departments', data),
  
  update: (id: string, data: UpdateBusinessUnitRequest) => put<BusinessUnit>(`/departments/${id}`, data),
  
  delete: (id: string) => del<void>(`/departments/${id}`),
  
  move: (id: string, data: MoveBusinessUnitRequest) => post<BusinessUnit>(`/departments/${id}/move`, data),
  
  getMembers: (id: string, params?: { page?: number; size?: number }) => 
    get<any>(`/departments/${id}/members`, { params }),
  
  getStatistics: (id: string) => get<{ memberCount: number; childCount: number }>(`/departments/${id}/statistics`)
}

// 为了向后兼容，保留 Department 类型别名
export type Department = BusinessUnit
