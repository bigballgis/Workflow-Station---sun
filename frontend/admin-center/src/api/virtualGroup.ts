import { get, post, put, del } from './request'

// ==================== 类型定义 ====================

export interface VirtualGroup {
  id: string
  name: string
  type: 'PROJECT' | 'WORK' | 'TEMPORARY' | 'TASK_HANDLER'
  description?: string
  validFrom?: string
  validTo?: string
  status: 'ACTIVE' | 'INACTIVE' | 'EXPIRED'
  memberCount: number
  createdAt: string
  createdBy?: string
  updatedAt: string
  updatedBy?: string
}

export interface VirtualGroupMember {
  id: string
  groupId: string
  userId: string
  username: string
  fullName: string
  employeeId?: string
  email?: string
  departmentId?: string
  departmentName?: string
  role: 'LEADER' | 'MEMBER'
  joinedAt: string
}

export interface GroupTask {
  id: string
  taskId: string
  taskName: string
  processInstanceId: string
  processName: string
  assignedAt: string
  dueDate?: string
  status: 'PENDING' | 'CLAIMED' | 'COMPLETED'
  claimedBy?: string
  claimedByName?: string
  claimedAt?: string
  priority?: number
}

export interface TaskHistory {
  id: string
  taskId: string
  action: string
  userId: string
  username: string
  timestamp: string
  details?: string
}

export interface VirtualGroupCreateRequest {
  name: string
  type: 'PROJECT' | 'WORK' | 'TEMPORARY' | 'TASK_HANDLER'
  description?: string
  validFrom?: string
  validTo?: string
}

export interface MemberRequest {
  userId: string
  role?: 'LEADER' | 'MEMBER'
}

export interface TaskClaimRequest {
  taskId?: string
  groupId?: string
  comment?: string
}

export interface TaskDelegationRequest {
  taskId?: string
  toUserId: string
  reason?: string
}

export interface VirtualGroupResult {
  success: boolean
  message?: string
  group?: VirtualGroup
}

// ==================== 虚拟组 CRUD API ====================

export const virtualGroupApi = {
  // 获取虚拟组列表
  list: (type?: string, status?: string) =>
    get<VirtualGroup[]>('/virtual-groups', { params: { type, status } }),

  // 根据ID获取虚拟组
  getById: (id: string) =>
    get<VirtualGroup>(`/virtual-groups/${id}`),

  // 创建虚拟组
  create: (data: VirtualGroupCreateRequest) =>
    post<VirtualGroupResult>('/virtual-groups', data),

  // 更新虚拟组
  update: (id: string, data: VirtualGroupCreateRequest) =>
    put<VirtualGroupResult>(`/virtual-groups/${id}`, data),

  // 删除虚拟组
  delete: (id: string) =>
    del<void>(`/virtual-groups/${id}`),

  // 激活虚拟组
  activate: (id: string) =>
    post<VirtualGroupResult>(`/virtual-groups/${id}/activate`),

  // 停用虚拟组
  deactivate: (id: string) =>
    post<VirtualGroupResult>(`/virtual-groups/${id}/deactivate`),

  // ==================== 成员管理 API ====================

  // 获取虚拟组成员
  getMembers: (groupId: string) =>
    get<VirtualGroupMember[]>(`/virtual-groups/${groupId}/members`),

  // 添加成员
  addMember: (groupId: string, data: MemberRequest) =>
    post<VirtualGroupResult>(`/virtual-groups/${groupId}/members`, data),

  // 移除成员
  removeMember: (groupId: string, userId: string) =>
    del<VirtualGroupResult>(`/virtual-groups/${groupId}/members/${userId}`),

  // 更新成员角色
  updateMemberRole: (groupId: string, userId: string, data: MemberRequest) =>
    put<VirtualGroupResult>(`/virtual-groups/${groupId}/members/${userId}/role`, data),

  // ==================== 任务管理 API ====================

  // 获取虚拟组任务
  getTasks: (groupId: string) =>
    get<GroupTask[]>(`/virtual-groups/${groupId}/tasks`),

  // 获取用户可见的所有组任务
  getMyGroupTasks: () =>
    get<GroupTask[]>('/virtual-groups/my-tasks'),

  // 认领任务
  claimTask: (groupId: string, taskId: string, data?: TaskClaimRequest) =>
    post<void>(`/virtual-groups/${groupId}/tasks/${taskId}/claim`, data || {}),

  // 委托任务
  delegateTask: (taskId: string, data: TaskDelegationRequest) =>
    post<void>(`/virtual-groups/tasks/${taskId}/delegate`, data),

  // 获取任务历史
  getTaskHistory: (groupId: string, taskId: string) =>
    get<TaskHistory[]>(`/virtual-groups/${groupId}/tasks/${taskId}/history`)
}
