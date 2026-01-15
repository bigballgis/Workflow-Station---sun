import { get, post, del } from './request'

/** 分配目标类型 */
export type AssignmentTargetType = 'USER' | 'BUSINESS_UNIT' | 'BUSINESS_UNIT_HIERARCHY' | 'VIRTUAL_GROUP'

/** 角色分配记录 */
export interface RoleAssignment {
  id: string
  roleId: string
  roleName: string
  targetType: AssignmentTargetType
  targetId: string
  targetName: string
  effectiveUserCount: number
  assignedAt: string
  assignedBy: string
  assignedByName: string
  validFrom?: string
  validTo?: string
}

/** 角色来源 */
export interface RoleSource {
  sourceType: AssignmentTargetType
  sourceId: string
  sourceName: string
  assignmentId: string
}

/** 有效用户 */
export interface EffectiveUser {
  userId: string
  username: string
  displayName: string
  employeeId?: string
  businessUnitId?: string
  businessUnitName?: string
  email?: string
  sources: RoleSource[]
}

/** 创建分配请求 */
export interface CreateAssignmentRequest {
  roleId: string
  targetType: AssignmentTargetType
  targetId: string
  validFrom?: string
  validTo?: string
}

const BASE = '/roles'

export const roleAssignmentApi = {
  /** 获取角色的分配记录列表 */
  getAssignments: (roleId: string) => 
    get<RoleAssignment[]>(`${BASE}/${roleId}/assignments`),
  
  /** 创建角色分配 */
  createAssignment: (roleId: string, data: Omit<CreateAssignmentRequest, 'roleId'>) => 
    post<RoleAssignment>(`${BASE}/${roleId}/assignments`, data),
  
  /** 删除角色分配 */
  deleteAssignment: (roleId: string, assignmentId: string) => 
    del<void>(`${BASE}/${roleId}/assignments/${assignmentId}`),
  
  /** 获取角色的有效用户列表 */
  getEffectiveUsers: (roleId: string) => 
    get<EffectiveUser[]>(`${BASE}/${roleId}/assignments/effective-users`)
}

/** 获取目标类型的显示文本 */
export const getTargetTypeText = (type: AssignmentTargetType): string => {
  const texts: Record<AssignmentTargetType, string> = {
    USER: '用户',
    BUSINESS_UNIT: '业务单元',
    BUSINESS_UNIT_HIERARCHY: '业务单元及下级',
    VIRTUAL_GROUP: '虚拟组'
  }
  return texts[type] || type
}

/** 获取目标类型的标签颜色 */
export const getTargetTypeTagType = (type: AssignmentTargetType): string => {
  const types: Record<AssignmentTargetType, string> = {
    USER: 'primary',
    BUSINESS_UNIT: 'success',
    BUSINESS_UNIT_HIERARCHY: 'warning',
    VIRTUAL_GROUP: 'info'
  }
  return types[type] || 'info'
}
