import { get, post, put, del } from './request'

export interface Role {
  id: string
  name: string
  code: string
  type: 'SYSTEM' | 'BUSINESS' | 'FUNCTION' | 'TEMPORARY'
  description?: string
  parentId?: string
  status: 'ACTIVE' | 'INACTIVE'
  memberCount: number
  createdAt: string
  updatedAt: string
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
  type: string
  description?: string
  parentId?: string
}

export interface UpdateRoleRequest {
  name?: string
  description?: string
}

export const roleApi = {
  list: (params?: { type?: string; status?: string }) => get<Role[]>('/roles', { params }),
  
  getById: (id: string) => get<Role>(`/roles/${id}`),
  
  create: (data: CreateRoleRequest) => post<Role>('/roles', data),
  
  update: (id: string, data: UpdateRoleRequest) => put<Role>(`/roles/${id}`, data),
  
  delete: (id: string) => del<void>(`/roles/${id}`),
  
  getPermissions: (id: string) => get<RolePermission[]>(`/roles/${id}/permissions`),
  
  updatePermissions: (id: string, permissions: RolePermission[]) => 
    put<void>(`/roles/${id}/permissions`, permissions),
  
  getMembers: (id: string, params?: { page?: number; size?: number }) => 
    get<any>(`/roles/${id}/members`, { params }),
  
  addMembers: (id: string, userIds: string[]) => post<void>(`/roles/${id}/members`, { userIds }),
  
  removeMembers: (id: string, userIds: string[]) => del<void>(`/roles/${id}/members`, { data: { userIds } })
}

export const permissionApi = {
  getTree: () => get<Permission[]>('/permissions/tree'),
  
  getByRole: (roleId: string) => get<Permission[]>(`/permissions/role/${roleId}`)
}
