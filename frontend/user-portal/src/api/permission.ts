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
  membershipType?: string
  assignedAt?: string
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
  /** 实际提交人（代办时与 applicantId 不同） */
  submittedByUserId?: string
  submittedByUsername?: string
  requestType:
    | 'VIRTUAL_GROUP'
    | 'VIRTUAL_GROUP_JOIN'
    | 'BUSINESS_UNIT_ROLE'
    | 'BUSINESS_UNIT_JOIN'
    | 'BUSINESS_UNIT_ROLE_REMOVAL'
    | 'BUSINESS_UNIT_EXIT'
  targetId: string
  targetName?: string
  roleIds?: string
  roleNames?: string[]
  /** MEMBER or LEADER for BUSINESS_UNIT_JOIN / BUSINESS_UNIT_ROLE. */
  membershipType?: string
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

/** 移除权限：按功能单元聚合的可选分配行 */
export interface RemovalAssignmentRow {
  assignmentId?: string
  businessUnitId: string
  businessUnitName?: string
  roleId: string
  roleName?: string
}

export interface FunctionUnitRemovalGroup {
  functionUnitId: string
  functionUnitName?: string
  functionUnitCode?: string
  assignments: RemovalAssignmentRow[]
}

export interface RemovalOptionsByFunctionUnitPayload {
  functionUnitGroups: FunctionUnitRemovalGroup[]
  otherAssignments: RemovalAssignmentRow[]
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

  /** 获取业务单元（扁平列表） */
  getBusinessUnits() {
    return request.get<BusinessUnit[]>('/permissions/business-units')
  },

  /** 获取业务单元树（保留 children 层级，供级联选择器） */
  getBusinessUnitsTree(config?: { skipGlobalErrorHandler?: boolean }) {
    return request.get<BusinessUnit[]>('/permissions/business-units/tree', config)
  },

  /** 获取业务单元绑定的角色 */
  getBusinessUnitRoles(businessUnitId: string, config?: { skipGlobalErrorHandler?: boolean }) {
    return request.get<RoleInfo[]>(`/permissions/business-units/${businessUnitId}/roles`, config)
  },

  /** 申请加入虚拟组 */
  requestVirtualGroup(data: VirtualGroupRequestDto) {
    return request.post<PermissionRequestRecord>('/permissions/request-virtual-group', data)
  },

  /** 申请加入业务单元（新API - 不需要选择角色） */
  requestBusinessUnit(data: { businessUnitId: string; reason: string; beneficiaryUserId?: string }) {
    return request.post<PermissionRequestRecord>('/permissions/request-business-unit', data)
  },

  /** 申请加入业务单元并指定 Eligible Role（走 portal 本地 PermissionController） */
  requestBusinessUnitRole(data: BusinessUnitRoleRequestDto & { beneficiaryUserId?: string }) {
    return request.post<PermissionRequestRecord>('/permissions/request-business-unit-role', data)
  },

  /** 申请退出业务单元成员（审批通过后生效）；可代办 */
  requestBusinessUnitExit(data: { businessUnitId: string; reason: string; beneficiaryUserId?: string }) {
    return request.post<PermissionRequestRecord>('/permissions/request-business-unit-exit', data)
  },

  /** 申请移除某业务单元下的业务角色（待 BU 审批人批准后生效） */
  requestBusinessUnitRoleRemoval(data: {
    businessUnitId: string
    roleId: string
    reason: string
    beneficiaryUserId?: string
  }) {
    return request.post<PermissionRequestRecord>('/permissions/request-business-unit-role-removal', data)
  },

  /** 按功能单元聚合受益人可移除的 BU 角色分配（依赖功能单元访问配置中的角色门槛） */
  getRemovalOptionsByFunctionUnit(beneficiaryUserId?: string) {
    return request.get<RemovalOptionsByFunctionUnitPayload>('/permissions/removal-options-by-function-unit', {
      params: beneficiaryUserId ? { beneficiaryUserId } : {}
    })
  },

  /** 搜索可申请的启用用户（代办选人） */
  searchUsersForDelegation(params: { keyword?: string; page?: number; size?: number }) {
    return request.get<{
      content: { userId: string; username: string; displayName?: string; email?: string }[]
      totalElements: number
      page: number
      size: number
    }>('/permissions/users/search', { params })
  },

  /** 获取用户可申请的业务单元（基于用户的 BU_BOUNDED 角色） */
  getApplicableBusinessUnits() {
    return request.get<BusinessUnit[]>('/permissions/available-business-units')
  },

  /** 获取我的申请记录 */
  getMyRequests(params?: { page?: number; size?: number; status?: string }) {
    return request.get<{ content: PermissionRequestRecord[]; totalElements: number }>('/permissions/requests', { params })
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

  /** 获取业务单元成员（含其在该单元的角色；仅该单元的审批人） */
  getBusinessUnitMembers(businessUnitId: string) {
    return request.get<MemberInfo[]>(`/members/business-units/${businessUnitId}`)
  },

  // Virtual groups are not available in the portal, nor is removing a member from a whole business unit
  // (SYS_ADMIN maintains membership in Admin Center); approvers can only remove a BU role.

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

  // ==================== 用户权限视图 API ====================

  /** 获取当前用户的权限视图 */
  getMyPermissionView() {
    return request.get<{
      roles: UserRoleAssignment[]
      virtualGroups: UserVirtualGroupMembership[]
      businessUnits: { id: string; name: string; joinedAt?: string }[]
      buBoundedRoles: { role: RoleInfo; activatedBusinessUnits: { id: string; name: string }[] }[]
    }>('/my-permissions')
  },

  // ==================== 旧的 API 方法（保留兼容） ====================

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

  /** Shared-list query for my-requests / approvals tabs */
  queryPermissionRequests(body: {
    page: number
    size: number
    filters?: Array<{ field: string; operator: string; value?: string; valueTo?: string }>
    sortField?: string
    sortDirection?: 'ASC' | 'DESC'
    scope: 'MY_PENDING' | 'MY_COMPLETED' | 'APPROVALS_PENDING' | 'APPROVALS_HISTORY'
  }) {
    return request.post<import('./task').PortalListPage<PermissionRequestRecord>>(
      '/permissions/requests/query',
      body,
    )
  },

  /** 获取申请详情 */
  getRequestDetail(requestId: number) {
    return request.get<PermissionRequestRecord>(`/permissions/requests/${requestId}`)
  }
}
