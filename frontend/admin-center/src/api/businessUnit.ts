import { get, post, put, del } from './request'

export interface BusinessUnit {
  id: string
  name: string
  code: string
  parentId?: string
  level: number
  path?: string
  managerId?: string
  managerName?: string
  secondaryManagerId?: string
  secondaryManagerName?: string
  phone?: string
  description?: string
  costCenter?: string
  location?: string
  sortOrder: number
  status: 'ACTIVE' | 'INACTIVE'
  memberCount?: number
  createdAt: string
  updatedAt: string
  children?: BusinessUnit[]
}

export interface CreateBusinessUnitRequest {
  name: string
  code: string
  parentId?: string
  managerId?: string
  secondaryManagerId?: string
  phone?: string
  description?: string
  costCenter?: string
  location?: string
  sortOrder?: number
}

export interface UpdateBusinessUnitRequest {
  name?: string
  managerId?: string
  secondaryManagerId?: string
  phone?: string
  description?: string
  costCenter?: string
  location?: string
  sortOrder?: number
}

export interface BusinessUnitRole {
  id: string
  businessUnitId: string
  roleId: string
  roleName?: string
  roleCode?: string
  createdAt: string
}

export interface Approver {
  id: string
  targetType: 'VIRTUAL_GROUP' | 'BUSINESS_UNIT'
  targetId: string
  userId: string
  userName?: string
  userFullName?: string
  createdAt: string
}

export const businessUnitApi = {
  // 获取业务单元树
  getTree: () => get<BusinessUnit[]>('/business-units/tree'),
  
  // 获取业务单元列表
  list: (params?: { parentId?: string; status?: string }) => 
    get<BusinessUnit[]>('/business-units', { params }),
  
  // 根据ID获取业务单元
  getById: (id: string) => get<BusinessUnit>(`/business-units/${id}`),
  
  // 创建业务单元
  create: (data: CreateBusinessUnitRequest) => post<BusinessUnit>('/business-units', data),
  
  // 更新业务单元
  update: (id: string, data: UpdateBusinessUnitRequest) => put<void>(`/business-units/${id}`, data),
  
  // 删除业务单元
  delete: (id: string) => del<void>(`/business-units/${id}`),
  
  // 获取业务单元成员
  getMembers: (id: string, params?: { page?: number; size?: number }) => 
    get<any>(`/business-units/${id}/members`, { params }),
  
  // 添加成员到业务单元
  addMember: (id: string, userId: string) => 
    post<void>(`/business-units/${id}/members`, { userId }),
  
  // 从业务单元移除成员
  removeMember: (id: string, userId: string) => 
    del<void>(`/business-units/${id}/members/${userId}`),
  
  // 获取子业务单元
  getChildren: (id: string) => get<BusinessUnit[]>(`/business-units/${id}/children`),
  
  // 搜索业务单元
  search: (keyword: string) => get<BusinessUnit[]>('/business-units/search', { params: { keyword } }),

  // 角色绑定相关
  // 获取业务单元绑定的角色
  getBoundRoles: (id: string) => get<BusinessUnitRole[]>(`/business-units/${id}/roles`),
  
  // 绑定角色到业务单元
  bindRole: (id: string, roleId: string) => post<void>(`/business-units/${id}/roles`, { roleId }),
  
  // 解绑角色
  unbindRole: (id: string, roleId: string) => del<void>(`/business-units/${id}/roles/${roleId}`),

  // 审批人相关
  // 获取业务单元审批人
  getApprovers: (id: string) => get<Approver[]>(`/approvers/business-units/${id}`),
  
  // 添加审批人
  addApprover: (id: string, userId: string) => 
    post<void>('/approvers', { targetType: 'BUSINESS_UNIT', targetId: id, userId }),
  
  // 移除审批人
  removeApprover: (approverId: string) => del<void>(`/approvers/${approverId}`)
}

// 为了向后兼容，导出 departmentApi 作为别名
export const departmentApi = businessUnitApi
