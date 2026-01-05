import request from './request'

export interface Permission {
  id: string
  name: string
  type: 'FUNCTION' | 'DATA' | 'TEMPORARY'
  validTo?: string
}

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

export interface PermissionRequestDto {
  type: 'FUNCTION' | 'DATA' | 'TEMPORARY'
  permissions: string[]
  reason: string
  validFrom?: string
  validTo?: string
}

export const permissionApi = {
  // 获取我的权限列表
  getMyPermissions() {
    return request.get<Permission[]>('/api/permissions/my')
  },

  // 提交权限申请
  submitRequest(data: PermissionRequestDto) {
    return request.post<PermissionRequest>('/api/permissions/request', data)
  },

  // 获取我的申请记录
  getMyRequests(params?: { page?: number; size?: number; status?: string }) {
    return request.get('/api/permissions/requests', { params })
  },

  // 获取申请详情
  getRequestDetail(requestId: number) {
    return request.get<PermissionRequest>(`/api/permissions/requests/${requestId}`)
  },

  // 取消申请
  cancelRequest(requestId: number) {
    return request.delete(`/api/permissions/requests/${requestId}`)
  },

  // 续期申请
  renewPermission(data: { permissionId: string; validTo: string; reason: string }) {
    return request.post<PermissionRequest>('/api/permissions/renew', data)
  },

  // 获取即将过期的权限
  getExpiringPermissions(days?: number) {
    return request.get<Permission[]>('/api/permissions/expiring', { params: { days } })
  }
}
