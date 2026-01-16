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

/** 业务单元信息 */
export interface BusinessUnit {
  id: string
  name: string
  code?: string
  parentId?: string
  children?: BusinessUnit[]
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

/** 用户业务单元角色 */
export interface UserBusinessUnitRole {
  id: string
  businessUnitId: string
  businessUnitName: string
  roleId: string
  roleName: string
  assignedAt?: string
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

/** 业务单元角色申请请求 */
export interface BusinessUnitRoleRequestDto {
  businessUnitId: string
  roleIds: string[]
  reason: string
}

/** 权限申请记录 */
export interface PermissionRequestRecord {
  id: string
  applicantId: string
  applicantName?: string
  applicantUsername?: string
  requestType: 'VIRTUAL_GROUP' | 'BUSINESS_UNIT_ROLE'
  targetId: string
  targetName?: string
  roleIds?: string
  roleNames?: string[]
  reason?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  approverId?: string
  approverName?: string
  approverComment?: string
  createdAt: string
  updatedAt?: string
  approvedAt?: string
}

/** 成员信息 */
export interface MemberInfo {
  userId: string
  username: string
  fullName?: string
  roles?: RoleInfo[]
  joinedAt?: string
}

/** 我的成员身份 */
export interface MyMembership {
  virtualGroups: UserVirtualGroupMembership[]
  businessUnitRoles: UserBusinessUnitRole[]
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
  // ==================== 权限申请 API ====================

  /** 获取可申请的业务角色（排除已拥有的） */
  getAvailableRoles() {
    return request.get<RoleInfo[]>('/permissions/available-roles')
  },

  /** 获取可加入的虚拟组（排除已加入的） */
  getAvailableVirtualGroups() {
    return request.get<VirtualGroupInfo[]>('/permissions/available-virtual-groups')
  },

  /** 获取业务单元树 */
  getBusinessUnits() {
    return request.get<BusinessUnit[]>('/permissions/business-units')
  },

  /** 获取业务单元绑定的角色 */
  getBusinessUnitRoles(businessUnitId: string) {
    return request.get<RoleInfo[]>(`/permissions/business-units/${businessUnitId}/roles`)
  },

  /** 申请加入虚拟组 */
  requestVirtualGroup(data: VirtualGroupRequestDto) {
    return request.post<PermissionRequestRecord>('/permissions/request-virtual-group', data)
  },

  /** 申请加入业务单元（新API - 不需要选择角色） */
  requestBusinessUnit(data: { businessUnitId: string; reason: string }) {
    return request.post<PermissionRequestRecord>('/permissions/request-business-unit', data)
  },

  /** 申请业务单元角色（旧API - 保留兼容） */
  requestBusinessUnitRole(data: BusinessUnitRoleRequestDto) {
    return request.post<PermissionRequestRecord>('/permission-requests/business-unit-role', data)
  },

  /** 获取用户可申请的业务单元（基于用户的 BU_BOUNDED 角色） */
  getApplicableBusinessUnits() {
    return request.get<BusinessUnit[]>('/permissions/available-business-units')
  },

  /** 获取加入业务单元后可激活的角色 */
  getActivatableRoles(businessUnitId: string) {
    return request.get<RoleInfo[]>(`/permission-requests/business-units/${businessUnitId}/activatable-roles`)
  },

  /** 获取我的申请记录 */
  getMyRequests(params?: { page?: number; size?: number; status?: string }) {
    return request.get<{ content: PermissionRequestRecord[]; totalElements: number }>('/permission-requests/my', { params })
  },

  /** 取消申请 */
  cancelRequest(requestId: string | number) {
    return request.delete(`/permissions/requests/${requestId}`)
  },

  // ==================== 审批 API ====================

  /** 获取待审批列表 */
  getPendingApprovals(params?: { page?: number; size?: number }) {
    return request.get<{ content: PermissionRequestRecord[]; totalElements: number }>('/permissions/approvals/pending', { params })
  },

  /** 批准申请 */
  approveRequest(requestId: string | number, comment?: string) {
    return request.post(`/permissions/approvals/${requestId}/approve`, { comment })
  },

  /** 拒绝申请 */
  rejectRequest(requestId: string | number, comment: string) {
    return request.post(`/permissions/approvals/${requestId}/reject`, { comment })
  },

  /** 检查当前用户是否是审批人 */
  isApprover() {
    return request.get<{ isApprover: boolean }>('/permissions/approvals/is-approver')
  },

  /** 获取审批历史 */
  getApprovalHistory(params?: { page?: number; size?: number }) {
    return request.get<{ content: PermissionRequestRecord[]; totalElements: number }>('/permissions/approvals/history', { params })
  },

  // ==================== 成员管理 API ====================

  /** 获取虚拟组成员 */
  getVirtualGroupMembers(virtualGroupId: string) {
    return request.get<MemberInfo[]>(`/members/virtual-groups/${virtualGroupId}`)
  },

  /** 获取业务单元成员 */
  getBusinessUnitMembers(businessUnitId: string) {
    return request.get<MemberInfo[]>(`/members/business-units/${businessUnitId}`)
  },

  /** 清退虚拟组成员 */
  removeVirtualGroupMember(virtualGroupId: string, userId: string) {
    return request.delete(`/members/virtual-groups/${virtualGroupId}/users/${userId}`)
  },

  /** 清退业务单元成员 */
  removeBusinessUnitMember(businessUnitId: string, userId: string) {
    return request.delete(`/members/business-units/${businessUnitId}/users/${userId}`)
  },

  /** 清退业务单元角色（旧API - 保留兼容） */
  removeBusinessUnitRole(businessUnitId: string, userId: string, roleId: string) {
    return request.delete(`/members/business-units/${businessUnitId}/users/${userId}/roles/${roleId}`)
  },

  // ==================== 退出角色 API ====================

  /** 获取我的成员身份 */
  getMyMemberships() {
    return request.get<MyMembership>('/exit/my-memberships')
  },

  /** 退出虚拟组 */
  exitVirtualGroup(virtualGroupId: string) {
    return request.post(`/exit/virtual-group/${virtualGroupId}`)
  },

  /** 退出业务单元 */
  exitBusinessUnit(businessUnitId: string) {
    return request.post(`/exit/business-unit/${businessUnitId}`)
  },

  /** 退出业务单元角色（旧API - 保留兼容） */
  exitBusinessUnitRoles(businessUnitId: string, roleIds: string[]) {
    return request.post(`/exit/business-unit/${businessUnitId}/roles`, { roleIds })
  },

  // ==================== 用户权限视图 API ====================

  /** 获取当前用户的权限视图 */
  getMyPermissionView() {
    return request.get<{
      roles: UserRoleAssignment[]
      virtualGroups: UserVirtualGroupMembership[]
      businessUnits: { id: string; name: string; joinedAt?: string }[]
      buBoundedRoles: { role: RoleInfo; activatedBusinessUnits: { id: string; name: string }[] }[]
      buUnboundedRoles: RoleInfo[]
    }>('/my-permissions')
  },

  /** 获取未激活的 BU-Bounded 角色 */
  getUnactivatedRoles() {
    return request.get<RoleInfo[]>('/my-permissions/unactivated-roles')
  },

  /** 检查是否需要显示提醒 */
  shouldShowReminder() {
    return request.get<{ shouldShow: boolean; roles: RoleInfo[] }>('/my-permissions/should-show-reminder')
  },

  /** 设置不再提醒偏好 */
  setDontRemind() {
    return request.post('/my-permissions/dont-remind')
  },

  /** 获取角色状态 */
  getRoleStatus(roleId: string) {
    return request.get<{
      roleId: string
      roleName: string
      roleType: string
      isActivated: boolean
      activatedBusinessUnits: { id: string; name: string }[]
    }>(`/my-permissions/roles/${roleId}/status`)
  },

  // ==================== 旧的 API 方法（保留兼容） ====================

  /** 申请角色 */
  requestRole(data: RoleRequestDto) {
    return request.post<PermissionRequestRecord>('/permissions/request-role', data)
  },

  /** 获取我的角色列表 */
  getMyRoles() {
    return request.get<UserRoleAssignment[]>('/permissions/my-roles')
  },

  /** 获取我的虚拟组列表 */
  getMyVirtualGroups() {
    return request.get<UserVirtualGroupMembership[]>('/permissions/my-virtual-groups')
  },

  /** 获取申请历史记录 */
  getRequestHistory(params?: { page?: number; size?: number; status?: string }) {
    return request.get<{ content: PermissionRequestRecord[]; totalElements: number }>('/permissions/requests', { params })
  },

  /** @deprecated 使用 getMyRoles 和 getMyVirtualGroups 替代 */
  getMyPermissions() {
    return request.get<Permission[]>('/permissions/my')
  },

  /** @deprecated 使用 requestRole 或 requestVirtualGroup 替代 */
  submitRequest(data: PermissionRequestDto) {
    return request.post<PermissionRequest>('/permissions/request', data)
  },

  /** 获取申请详情 */
  getRequestDetail(requestId: number) {
    return request.get<PermissionRequestRecord>(`/permissions/requests/${requestId}`)
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
