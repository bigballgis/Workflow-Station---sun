import { get, post, put, del } from './request'

export interface Department {
  id: string
  name: string
  code: string
  parentId?: string
  parentName?: string
  managerId?: string
  managerName?: string
  leaderId?: string  // alias for managerId (for backward compatibility)
  leaderName?: string  // alias for managerName (for backward compatibility)
  secondaryManagerId?: string
  secondaryManagerName?: string
  level: number
  sortOrder: number
  status: 'ACTIVE' | 'INACTIVE'
  memberCount: number
  children?: Department[]
  createdAt: string
  updatedAt: string
}

export interface CreateDepartmentRequest {
  name: string
  code: string
  parentId?: string
  managerId?: string
  secondaryManagerId?: string
  sortOrder?: number
}

export interface UpdateDepartmentRequest {
  name?: string
  managerId?: string
  secondaryManagerId?: string
  sortOrder?: number
}

export interface MoveDepartmentRequest {
  newParentId?: string
  sortOrder?: number
}

export const organizationApi = {
  getTree: () => get<Department[]>('/departments/tree'),
  
  getById: (id: string) => get<Department>(`/departments/${id}`),
  
  create: (data: CreateDepartmentRequest) => post<Department>('/departments', data),
  
  update: (id: string, data: UpdateDepartmentRequest) => put<Department>(`/departments/${id}`, data),
  
  delete: (id: string) => del<void>(`/departments/${id}`),
  
  move: (id: string, data: MoveDepartmentRequest) => post<Department>(`/departments/${id}/move`, data),
  
  getMembers: (id: string, params?: { page?: number; size?: number }) => 
    get<any>(`/departments/${id}/members`, { params }),
  
  getStatistics: (id: string) => get<{ memberCount: number; childCount: number }>(`/departments/${id}/statistics`)
}
