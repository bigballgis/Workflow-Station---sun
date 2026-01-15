import { get, post, put, del } from './request'

export interface User {
  id: string
  username: string
  fullName: string
  email: string
  employeeId?: string
  businessUnitId?: string
  businessUnitName?: string
  /** @deprecated Use businessUnitId instead */
  departmentId?: string
  /** @deprecated Use businessUnitName instead */
  departmentName?: string
  position?: string
  entityManagerId?: string
  entityManagerName?: string
  functionManagerId?: string
  functionManagerName?: string
  status: 'ACTIVE' | 'DISABLED' | 'LOCKED' | 'PENDING'
  lastLoginAt?: string
  createdAt: string
  updatedAt?: string
}

export interface UserDetail extends User {
  entityManagerName?: string
  functionManagerName?: string
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
  businessUnitId?: string
  /** @deprecated Use businessUnitId instead */
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
  employeeId?: string
  businessUnitId?: string
  /** @deprecated Use businessUnitId instead */
  departmentId?: string
  position?: string
  entityManagerId?: string
  functionManagerId?: string
  initialPassword: string
  roleIds?: string[]
}

export interface UpdateUserRequest {
  fullName?: string
  email?: string
  employeeId?: string
  businessUnitId?: string
  /** @deprecated Use businessUnitId instead */
  departmentId?: string
  position?: string
  entityManagerId?: string
  functionManagerId?: string
  roleIds?: string[]
}

export interface UserBusinessUnitRole {
  id: string
  userId: string
  businessUnitId: string
  businessUnitName?: string
  roleId: string
  roleName?: string
  roleCode?: string
  createdAt: string
}

/** 用户业务单元成员身份 */
export interface UserBusinessUnitMembership {
  id: string
  name: string
  code?: string
  path?: string
}

/** 用户虚拟组成员身份 */
export interface UserVirtualGroupMembership {
  groupId: string
  groupName: string
  groupDescription?: string
  joinedAt: string
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

// 用户管理API - 使用默认baseURL (/api/v1/admin)
export const userApi = {
  list: (params: UserQuery) => get<PageResult<User>>('/users', { params }),
  
  getById: (id: string) => get<UserDetail>(`/users/${id}`),
  
  create: (data: CreateUserRequest) => post<{ userId: string; username: string }>('/users', data),
  
  update: (id: string, data: UpdateUserRequest) => put<void>(`/users/${id}`, data),
  
  delete: (id: string) => del<void>(`/users/${id}`),
  
  updateStatus: (id: string, data: StatusUpdateRequest) => 
    put<void>(`/users/${id}/status`, data),
  
  resetPassword: (id: string) => 
    post<string>(`/users/${id}/reset-password`, null),
  
  batchImport: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return post<ImportResult>('/users/batch-import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  exportTemplate: () => get<Blob>('/users/export-template', { responseType: 'blob' }),

  // 用户业务单元成员身份（只读，角色通过虚拟组获取）
  getBusinessUnits: (userId: string) => 
    get<UserBusinessUnitMembership[]>(`/users/${userId}/business-units`),
  
  // 用户虚拟组成员身份
  getVirtualGroups: (userId: string) =>
    get<UserVirtualGroupMembership[]>(`/users/${userId}/virtual-groups`),

  // 用户角色（通过虚拟组获取）
  getRoles: (userId: string) =>
    get<{ id: string; name: string; code: string; type: string }[]>(`/users/${userId}/roles`),

  // @deprecated - 业务单元角色管理已移除，角色通过虚拟组获取
  getBusinessUnitRoles: (userId: string) => 
    get<UserBusinessUnitRole[]>(`/users/${userId}/business-unit-roles`),
  
  // @deprecated - 业务单元角色分配已移除，角色通过虚拟组获取
  assignBusinessUnitRole: (userId: string, businessUnitId: string, roleId: string) =>
    post<void>(`/users/${userId}/business-unit-roles`, { businessUnitId, roleId }),
  
  // @deprecated - 业务单元角色移除已移除，角色通过虚拟组获取
  removeBusinessUnitRole: (userId: string, roleId: string) =>
    del<void>(`/users/${userId}/business-unit-roles/${roleId}`)
}
