import { PageResult, type AdminListPage } from '@/types/common'

export type { PageResult, AdminListPage } from '@/types/common'
import { get, post, put, del } from './request'
import type { ListColumnFilterRequest } from '@platform-shared/list/columnMeta'

export interface User {
  id: string
  username: string
  fullName: string
  email: string
  employeeId?: string
  businessUnitId?: string
  businessUnitName?: string
  position?: string
  entityManagerId?: string
  entityManagerName?: string
  functionManagerId?: string
  functionManagerName?: string
  status: 'ACTIVE' | 'DISABLED' | 'LOCKED' | 'PENDING' | 'INACTIVE'
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
  loginPlatform?: string
  success: boolean
  failureReason?: string
}

export interface UserQuery {
  keyword?: string
  businessUnitId?: string
  status?: string
  page?: number
  size?: number
}

export interface UserListQuery {
  page: number
  size: number
  keyword?: string
  status?: string
  filters?: ListColumnFilterRequest[]
  sortField?: string
  sortDirection?: 'ASC' | 'DESC'
}

export interface CreateUserRequest {
  username: string
  fullName: string
  email: string
  employeeId?: string
  businessUnitId?: string
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

  query: (body: UserListQuery) => post<AdminListPage<User>>('/users/query', body),
  
  getById: (id: string) => get<UserDetail>(`/users/${id}`),
  
  create: (data: CreateUserRequest) => post<{ userId: string; username: string }>('/users', data),
  
  update: (id: string, data: UpdateUserRequest) => put<void>(`/users/${id}`, data),
  
  delete: (id: string) => del<void>(`/users/${id}`),
  
  updateStatus: (id: string, data: StatusUpdateRequest) => 
    put<void>(`/users/${id}/status`, data),
  
  batchImport: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return post<ImportResult>('/users/batch-import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  exportTemplate: () => get<Blob>('/users/export-template', { responseType: 'blob' }),

  /**
   * profileContext 可选：顶栏等场景传 ADMIN 仅展示管理端相关身份；不传则全量（用户详情等）
   */
  getBusinessUnits: (userId: string, profileContext?: 'PORTAL' | 'ADMIN' | 'DEVELOPER') =>
    get<UserBusinessUnitMembership[]>(`/users/${userId}/business-units`, {
      params: profileContext ? { profileContext } : undefined
    }),

  getVirtualGroups: (userId: string, profileContext?: 'PORTAL' | 'ADMIN' | 'DEVELOPER') =>
    get<UserVirtualGroupMembership[]>(`/users/${userId}/virtual-groups`, {
      params: profileContext ? { profileContext } : undefined
    }),

  getRoles: (userId: string, profileContext?: 'PORTAL' | 'ADMIN' | 'DEVELOPER') =>
    get<{ id: string; name: string; code: string; type: string }[]>(`/users/${userId}/roles`, {
      params: profileContext ? { profileContext } : undefined
    }),

  /** 用户在业务单元下的角色（UBR），与门户工作台上下文、流程 activeBusinessUnitId 对齐 */
  getBusinessUnitRoles: (userId: string) =>
    get<UserBusinessUnitRole[]>(`/users/${userId}/business-unit-roles`),

  assignBusinessUnitRole: (userId: string, businessUnitId: string, roleId: string) =>
    post<void>(`/users/${userId}/business-unit-roles`, { businessUnitId, roleId }),

  removeBusinessUnitRole: (userId: string, businessUnitId: string, roleId: string) =>
    del<void>(`/users/${userId}/business-unit-roles/${businessUnitId}/${roleId}`)
}
