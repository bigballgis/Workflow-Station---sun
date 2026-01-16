import { get, post, put, del } from './request'

/** 角色类型 */
export type RoleType = 'BU_BOUNDED' | 'BU_UNBOUNDED' | 'BUSINESS' | 'ADMIN' | 'DEVELOPER'

export interface Role {
  id: string
  name: string
  code: string
  type: RoleType
  description?: string
  parentRoleId?: string
  status: 'ACTIVE' | 'INACTIVE'
  isSystem?: boolean
  memberCount?: number
  createdAt: string
  updatedAt?: string
}

export interface Permission {
  id: string
  name: string
  code: string
  resourceType: string
  actions: string[]
  parentId?: string
  children?: Permission[]
}

export interface RolePermission {
  roleId: string
  permissionId: string
  actions: string[]
  conditions?: {
    timeRange?: { start: string; end: string }
    ipWhitelist?: string[]
    dataScope?: string
  }
}

export interface CreateRoleRequest {
  name: string
  code: string
  type: RoleType
  description?: string
  parentRoleId?: string
}

export interface UpdateRoleRequest {
  name?: string
  description?: string
}

// 角色管理API - 使用独立的baseURL
const ROLE_BASE = '/api/v1/roles'
const PERMISSION_BASE = '/api/v1/permissions'

export const roleApi = {
  /** 获取角色列表，支持按类型筛选 */
  list: (params?: { type?: RoleType; status?: string }) => 
    get<Role[]>(ROLE_BASE, { params, baseURL: '' }),
  
  /** 获取业务角色列表（用于功能单元访问配置） */
  getBusinessRoles: () => 
    get<Role[]>(`${ROLE_BASE}/business`, { baseURL: '' }),
  
  /** 获取开发角色列表 */
  getDeveloperRoles: () => 
    get<Role[]>(`${ROLE_BASE}/developer`, { baseURL: '' }),
  
  getById: (id: string) => get<Role>(`${ROLE_BASE}/${id}`, { baseURL: '' }),
  
  create: (data: CreateRoleRequest) => post<Role>(ROLE_BASE, data, { baseURL: '' }),
  
  update: (id: string, data: UpdateRoleRequest) => put<Role>(`${ROLE_BASE}/${id}`, data, { baseURL: '' }),
  
  delete: (id: string) => del<void>(`${ROLE_BASE}/${id}`, { baseURL: '' }),
  
  getPermissions: (id: string) => get<RolePermission[]>(`${ROLE_BASE}/${id}/permissions`, { baseURL: '' }),
  
  updatePermissions: (id: string, permissions: RolePermission[]) => 
    put<void>(`${ROLE_BASE}/${id}/permissions`, permissions, { baseURL: '' }),
  
  getMembers: (id: string, params?: { page?: number; size?: number }) => 
    get<any>(`${ROLE_BASE}/${id}/members`, { params, baseURL: '' }),
  
  addMember: (roleId: string, userId: string, reason?: string) => 
    post<void>(`${ROLE_BASE}/${roleId}/members/${userId}`, null, { 
      params: { reason }, 
      baseURL: '' 
    }),
  
  removeMember: (roleId: string, userId: string, reason?: string) => 
    del<void>(`${ROLE_BASE}/${roleId}/members/${userId}`, { 
      params: { reason }, 
      baseURL: '' 
    }),
  
  batchAddMembers: (roleId: string, userIds: string[], reason?: string) => 
    post<any>(`${ROLE_BASE}/${roleId}/members/batch`, { userIds, reason }, { baseURL: '' }),
  
  batchRemoveMembers: (roleId: string, userIds: string[], reason?: string) => 
    del<any>(`${ROLE_BASE}/${roleId}/members/batch`, { 
      data: { userIds, reason }, 
      baseURL: '' 
    }),
  
  getMemberCount: (id: string) => get<number>(`${ROLE_BASE}/${id}/members/count`, { baseURL: '' }),
  
  getHistory: (id: string) => get<any[]>(`${ROLE_BASE}/${id}/history`, { baseURL: '' })
}

export const permissionApi = {
  getTree: () => get<Permission[]>(`${PERMISSION_BASE}/tree`, { baseURL: '' }),
  
  getByRole: (roleId: string) => get<Permission[]>(`${PERMISSION_BASE}/role/${roleId}`, { baseURL: '' })
}
