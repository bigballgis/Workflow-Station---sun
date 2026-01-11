import request from './request'

// ==================== 新的类型定义 ====================

/** 角色信息 */
export interface RoleInfo {
  id: string
  name: string
  code: string
  description?: string
  type?: string
}

/** 虚拟组信息 */
export interface VirtualGroupInfo {
  id: string
  name: string
  description?: string
  boundRoles?: RoleInfo[]
  status?: string
}

/** 组织单元/部门信息 */
export interface OrganizationUnit {
  id: string
  name: string
  code?: string
  parentId?: string
  children?: OrganizationUnit[]
}

/** 用户角色分配 */
export interface UserRoleAssignment {
  roleId: string
  roleName: string
  roleCode?: string
  organizationUnitId?: string
  organizationUnitName?: string
  assignedAt?: string
  source?: 'DIRECT' | 'VIRTUAL_GROUP'
}

/** 用户虚拟组成员身份 */
export interface UserVirtualGroupMembership {
  groupId: string
  groupName: string
  joinedAt?: string
  role?: string
  boundRoles?: RoleInfo[]
}

/** 角色申请请求 */
export interface RoleRequestDto {
  roleId: string
  organizationUnitId: string
  reason: string
}

/** 虚拟组申请请求 */
export interface VirtualGroupRequestDto {
  virtualGroupId: string
  reason: string
}

/** 权限申请记录 */
export interface PermissionRequestRecord {
  id: number
  applicantId: string
  requestType: 'ROLE_ASSIGNMENT' | 'VIRTUAL_GROUP_JOIN' | 'FUNCTION' | 'DATA' | 'TEMPORARY'
  // 角色申请字段
  roleId?: string
  roleName?: string
  organizationUnitId?: string
  organizationUnitName?: string
  // 虚拟组申请字段
  virtualGroupId?: string
  virtualGroupName?: string
  // 旧字段（兼容）
  permissions?: string[]
  reason: string
  validFrom?: string
  validTo?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  approverId?: string
  approveTime?: string
  approveComment?: string
  createdAt: string
  updatedAt?: string
}

// ==================== 旧的类型定义（保留兼容） ====================

/** @deprecated 使用 RoleInfo 或 VirtualGroupInfo 替代 */
export interface Permission {
  id: string
  name: string
  type: 'FUNCTION' | 'DATA' | 'TEMPORARY' | 'ROLE' | 'VIRTUAL_GROUP'
  validTo?: string
}

/** @deprecated 使用 PermissionRequestRecord 替代 */
export interface PermissionRequest {
  id: number
  applicantId: string
  requestType: 'FUNCTION' | 'DATA' | 'TEMPORARY'
  permissions: string[]
  reason: string
  validFrom?: string
  validTo?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  approverId?: string
  approveTime?: string
  approveComment?: string
  createdAt: string
}

/** @deprecated 使用 RoleRequestDto 或 VirtualGroupRequestDto 替代 */
export interface PermissionRequestDto {
  type: 'FUNCTION' | 'DATA' | 'TEMPORARY'
  permissions: string[]
  reason: string
  validFrom?: string
  validTo?: string
}

// ==================== API 方法 ====================

export const permissionApi = {
  // ==================== 新的 API 方法 ====================

  /** 获取可申请的业务角色（排除已拥有的） */
  getAvailableRoles() {
    return request.get<RoleInfo[]>('/permissions/available-roles')
  },

  /** 获取可加入的虚拟组（排除已加入的） */
  getAvailableVirtualGroups() {
    return request.get<VirtualGroupInfo[]>('/permissions/available-virtual-groups')
  },

  /** 申请角色 */
  requestRole(data: RoleRequestDto) {
    return request.post<PermissionRequestRecord>('/permissions/request-role', data)
  },

  /** 申请加入虚拟组 */
  requestVirtualGroup(data: VirtualGroupRequestDto) {
    return request.post<PermissionRequestRecord>('/permissions/request-virtual-group', data)
  },

  /** 获取我的角色列表 */
  getMyRoles() {
    return request.get<UserRoleAssignment[]>('/permissions/my-roles')
  },

  /** 获取我的虚拟组列表 */
  getMyVirtualGroups() {
    return request.get<UserVirtualGroupMembership[]>('/permissions/my-virtual-groups')
  },

  /** 获取部门列表 */
  getDepartments() {
    return request.get<OrganizationUnit[]>('/permissions/departments')
  },

  /** 获取申请历史记录 */
  getRequestHistory(params?: { page?: number; size?: number; status?: string }) {
    return request.get<{ content: PermissionRequestRecord[]; totalElements: number }>('/permissions/requests', { params })
  },

  // ==================== 旧的 API 方法（保留兼容） ====================

  /** @deprecated 使用 getMyRoles 和 getMyVirtualGroups 替代 */
  getMyPermissions() {
    return request.get<Permission[]>('/permissions/my')
  },

  /** @deprecated 使用 requestRole 或 requestVirtualGroup 替代 */
  submitRequest(data: PermissionRequestDto) {
    return request.post<PermissionRequest>('/permissions/request', data)
  },

  /** @deprecated 使用 getRequestHistory 替代 */
  getMyRequests(params?: { page?: number; size?: number; status?: string }) {
    return request.get('/permissions/requests', { params })
  },

  /** 获取申请详情 */
  getRequestDetail(requestId: number) {
    return request.get<PermissionRequestRecord>(`/permissions/requests/${requestId}`)
  },

  /** 取消申请 */
  cancelRequest(requestId: number) {
    return request.delete(`/permissions/requests/${requestId}`)
  },

  /** @deprecated 新的权限模型不需要续期 */
  renewPermission(data: { permissionId: string; validTo: string; reason: string }) {
    return request.post<PermissionRequest>('/permissions/renew', data)
  },

  /** @deprecated 新的权限模型不需要过期检查 */
  getExpiringPermissions(days?: number) {
    return request.get<Permission[]>('/permissions/expiring', { params: { days } })
  }
}
