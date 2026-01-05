import { get, post, put, del } from './request'

export interface User {
  id: string
  username: string
  realName: string
  email: string
  phone?: string
  departmentId?: string
  departmentName?: string
  status: 'ENABLED' | 'DISABLED' | 'LOCKED'
  createdAt: string
  updatedAt: string
}

export interface UserQuery {
  username?: string
  realName?: string
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
  realName: string
  email: string
  phone?: string
  departmentId?: string
  password: string
  activateImmediately?: boolean
}

export interface UpdateUserRequest {
  realName?: string
  email?: string
  phone?: string
  departmentId?: string
}

export interface ImportResult {
  totalCount: number
  successCount: number
  failedCount: number
  errors: { row: number; message: string }[]
}

export const userApi = {
  list: (params: UserQuery) => get<PageResult<User>>('/users', { params }),
  
  getById: (id: string) => get<User>(`/users/${id}`),
  
  create: (data: CreateUserRequest) => post<User>('/users', data),
  
  update: (id: string, data: UpdateUserRequest) => put<User>(`/users/${id}`, data),
  
  delete: (id: string) => del<void>(`/users/${id}`),
  
  enable: (id: string) => post<User>(`/users/${id}/enable`),
  
  disable: (id: string) => post<User>(`/users/${id}/disable`),
  
  lock: (id: string) => post<User>(`/users/${id}/lock`),
  
  unlock: (id: string) => post<User>(`/users/${id}/unlock`),
  
  resetPassword: (id: string, newPassword: string) => 
    post<void>(`/users/${id}/reset-password`, { newPassword }),
  
  batchImport: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return post<ImportResult>('/users/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  exportTemplate: () => get<Blob>('/users/import-template', { responseType: 'blob' })
}
