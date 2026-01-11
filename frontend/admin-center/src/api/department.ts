import { get, post, put, del } from './request'

export interface Department {
  id: string
  name: string
  code: string
  parentId?: string
  level: number
  path?: string
  managerId?: string
  managerName?: string
  phone?: string
  description?: string
  costCenter?: string
  location?: string
  sortOrder: number
  status: 'ACTIVE' | 'INACTIVE'
  memberCount?: number
  createdAt: string
  updatedAt: string
  children?: Department[]
}

export interface CreateDepartmentRequest {
  name: string
  code: string
  parentId?: string
  managerId?: string
  phone?: string
  description?: string
  costCenter?: string
  location?: string
  sortOrder?: number
}

export interface UpdateDepartmentRequest {
  name?: string
  managerId?: string
  phone?: string
  description?: string
  costCenter?: string
  location?: string
  sortOrder?: number
}

export const departmentApi = {
  // 获取部门树
  getTree: () => get<Department[]>('/departments/tree'),
  
  // 获取部门列表
  list: (params?: { parentId?: string; status?: string }) => 
    get<Department[]>('/departments', { params }),
  
  // 根据ID获取部门
  getById: (id: string) => get<Department>(`/departments/${id}`),
  
  // 创建部门
  create: (data: CreateDepartmentRequest) => post<Department>('/departments', data),
  
  // 更新部门
  update: (id: string, data: UpdateDepartmentRequest) => put<Department>(`/departments/${id}`, data),
  
  // 删除部门
  delete: (id: string) => del<void>(`/departments/${id}`),
  
  // 获取部门成员
  getMembers: (id: string, params?: { page?: number; size?: number }) => 
    get<any>(`/departments/${id}/members`, { params }),
  
  // 获取子部门
  getChildren: (id: string) => get<Department[]>(`/departments/${id}/children`),
  
  // 获取部门路径（从根到当前部门）
  getPath: (id: string) => get<Department[]>(`/departments/${id}/path`)
}
