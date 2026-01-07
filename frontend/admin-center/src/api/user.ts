import { get, post, put, del } from './request'

export interface User {
  id: string
  username: string
  fullName: string
  email: string
  phone?: string
  employeeId?: string
  departmentId?: string
  departmentName?: string
  position?: string
  status: 'ACTIVE' | 'DISABLED' | 'LOCKED' | 'PENDING'
  lastLoginAt?: string
  createdAt: string
  updatedAt?: string
}

export interface UserDetail extends User {
  mustChangePassword?: boolean
  passwordExpiredAt?: string
  lastLoginIp?: string
  createdBy?: string
  updatedBy?: string
  roles: RoleInfo[]
  loginHistory: LoginHistory[]
}

export interface RoleInfo {
  roleId?: string
  roleCode: string
  roleName: string
  description?: string
}

export interface LoginHistory {
  loginTime: string
  ipAddress: string
  userAgent?: string
  success: boolean
  failureReason?: string
}

export interface UserQuery {
  keyword?: string
  departmentId?: string
  status?: string
  page?: number
  size?: number
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface CreateUserRequest {
  username: string
  fullName: string
  email: string
  phone?: string
  employeeId?: string
  departmentId?: string
  position?: string
  initialPassword: string
  roleIds?: string[]
}

export interface UpdateUserRequest {
  fullName?: string
  email?: string
  phone?: string
  employeeId?: string
  departmentId?: string
  position?: string
  roleIds?: string[]
}

export interface StatusUpdateRequest {
  status: 'ACTIVE' | 'DISABLED' | 'LOCKED'
  reason?: string
}

export interface ImportResult {
  total: number
  success: number
  failed: number
  errors: ImportError[]
}

export interface ImportError {
  row: number
  field: string
  message: string
  value?: string
}

// 用户管理API - 使用独立的baseURL
const USER_BASE = '/api/v1/users'

export const userApi = {
  list: (params: UserQuery) => get<PageResult<User>>(USER_BASE, { params, baseURL: '' }),
  
  getById: (id: string) => get<UserDetail>(`${USER_BASE}/${id}`, { baseURL: '' }),
  
  create: (data: CreateUserRequest) => post<{ userId: string; username: string }>(USER_BASE, data, { baseURL: '' }),
  
  update: (id: string, data: UpdateUserRequest) => put<void>(`${USER_BASE}/${id}`, data, { baseURL: '' }),
  
  delete: (id: string) => del<void>(`${USER_BASE}/${id}`, { baseURL: '' }),
  
  updateStatus: (id: string, data: StatusUpdateRequest) => 
    put<void>(`${USER_BASE}/${id}/status`, data, { baseURL: '' }),
  
  resetPassword: (id: string) => 
    post<string>(`${USER_BASE}/${id}/reset-password`, null, { baseURL: '' }),
  
  batchImport: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return post<ImportResult>(`${USER_BASE}/batch-import`, formData, {
      baseURL: '',
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  exportTemplate: () => get<Blob>(`${USER_BASE}/export-template`, { baseURL: '', responseType: 'blob' })
}
