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

// 角色管理API
const ROLE_BASE = '/roles'
const PERMISSION_BASE = '/permissions'

export const roleApi = {
  /** 获取角色列表，支持按类型筛选 */
  list: (params?: { type?: RoleType; status?: string }) => 
    get<Role[]>(ROLE_BASE, { params }),
  
  /** 获取业务角色列表（用于功能单元访问配置） */
  getBusinessRoles: () => 
    get<Role[]>(`${ROLE_BASE}/business`),
  
  /** 获取开发角色列表 */
  getDeveloperRoles: () => 
    get<Role[]>(`${ROLE_BASE}/developer`),
  
  getById: (id: string) => get<Role>(`${ROLE_BASE}/${id}`),
  
  create: (data: CreateRoleRequest) => post<Role>(ROLE_BASE, data),
  
  update: (id: string, data: UpdateRoleRequest) => put<Role>(`${ROLE_BASE}/${id}`, data),
  
  delete: (id: string) => del<void>(`${ROLE_BASE}/${id}`),
  
  getPermissions: (id: string) => get<RolePermission[]>(`${ROLE_BASE}/${id}/permissions`),
  
  updatePermissions: (id: string, permissions: RolePermission[]) => 
    put<void>(`${ROLE_BASE}/${id}/permissions`, permissions),
  
  getMembers: (id: string, params?: { page?: number; size?: number }) => 
    get<any>(`${ROLE_BASE}/${id}/members`, { params }),
  
  addMember: (roleId: string, userId: string, reason?: string) => 
    post<void>(`${ROLE_BASE}/${roleId}/members/${userId}`, null, { 
      params: { reason }
    }),
  
  removeMember: (roleId: string, userId: string, reason?: string) => 
    del<void>(`${ROLE_BASE}/${roleId}/members/${userId}`, { 
      params: { reason }
    }),
  
  batchAddMembers: (roleId: string, userIds: string[], reason?: string) => 
    post<any>(`${ROLE_BASE}/${roleId}/members/batch`, { userIds, reason }),
  
  batchRemoveMembers: (roleId: string, userIds: string[], reason?: string) => 
    del<any>(`${ROLE_BASE}/${roleId}/members/batch`, { 
      data: { userIds, reason }
    }),
  
  getMemberCount: (id: string) => get<number>(`${ROLE_BASE}/${id}/members/count`),
  
  getHistory: (id: string) => get<any[]>(`${ROLE_BASE}/${id}/history`)
}

export const permissionApi = {
  getTree: () => get<Permission[]>(`${PERMISSION_BASE}`),
  
  getByRole: (roleId: string) => get<Permission[]>(`${PERMISSION_BASE}/role/${roleId}`)
}
