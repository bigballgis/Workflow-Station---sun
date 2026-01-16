import { get } from './request'

export type PermissionRequestType = 'VIRTUAL_GROUP' | 'BUSINESS_UNIT_ROLE'
export type PermissionRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface PermissionRequest {
  id: string
  applicantId: string
  applicantName?: string
  applicantUsername?: string
  requestType: PermissionRequestType
  targetId: string
  targetName?: string
  roleIds?: string
  roleNames?: string[]
  reason?: string
  status: PermissionRequestStatus
  approverId?: string
  approverName?: string
  approverComment?: string
  createdAt: string
  updatedAt?: string
  approvedAt?: string
}

export interface PermissionRequestQuery {
  status?: PermissionRequestStatus
  requestType?: PermissionRequestType
  applicantId?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export const permissionRequestApi = {
  // 获取所有申请记录
  list: (params?: PermissionRequestQuery) => 
    get<PageResult<PermissionRequest>>('/permission-requests', { params }),
  
  // 获取申请详情
  getById: (id: string) => 
    get<PermissionRequest>(`/permission-requests/${id}`)
}
