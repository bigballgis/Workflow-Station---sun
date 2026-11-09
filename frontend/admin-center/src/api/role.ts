import type { ReferenceNotice } from '@/types/referenceNotice'
import { get, post, put, del } from './request'
import type { AdminListPage, PageResult } from '@/types/common'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'

/** 角色类型 */
export type RoleType = 'BU_BOUNDED' | 'BUSINESS' | 'ADMIN' | 'AUDITOR' | 'DEVELOPER'

export interface Role {
  id: string
  name: string
  code: string
  type: RoleType
  displayName?: string
  parentRoleId?: string
  status: 'ACTIVE' | 'INACTIVE'
  isSystem?: boolean
  memberCount?: number
  createdAt: string
  updatedAt?: string
}

export interface CreateRoleRequest {
  name: string
  code: string
  type: RoleType
  displayName?: string
  parentRoleId?: string
}

export interface UpdateRoleRequest {
  name?: string
  displayName?: string
  status?: 'ACTIVE' | 'INACTIVE'
}

export interface RoleListQuery {
  page: number
  size: number
  tab: 'SYSTEM' | 'CUSTOM'
  type?: RoleType
  filters?: Array<ListColumnFilter & { field: string }>
  sortField?: string
  sortDirection?: 'ASC' | 'DESC'
}

// 角色管理API
const ROLE_BASE = '/roles'

export const roleApi = {
  /** 获取角色列表，支持按类型筛选和分页 */
  list: (params?: { type?: RoleType; status?: string; page?: number; size?: number }) => 
    get<PageResult<Role>>(ROLE_BASE, { params }),

  query: (body: RoleListQuery) =>
    post<AdminListPage<Role>>(`${ROLE_BASE}/query`, body),
  
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
  
  /** Function units and other places that use the role; shown before a change or delete (notice only). */
  getReferences: (id: string) => get<ReferenceNotice>(`${ROLE_BASE}/${id}/references`),

  /** Who holds the role: SYSTEM group members, team lists, or business unit memberships, by role type. */
  getHolders: (id: string) => get<RoleHolder[]>(`${ROLE_BASE}/${id}/holders`)
}

/** Someone holding a role, and through which group, team or business unit. */
export interface RoleHolder {
  userId: string
  username: string
  fullName?: string
  employeeId?: string
  email?: string
  status: string
  scopeType: 'GROUP' | 'TEAM' | 'BUSINESS_UNIT'
  scopeName: string
}
