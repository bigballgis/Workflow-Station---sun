import request from './request'

export interface AdminCommonTable {
  id: number
  code: string
  name: string
  description?: string
  status: string
  version?: string
  enabled: boolean
  deployedAt?: string
  deployedBy?: string
  updatedBy?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminCommonTableDeployment {
  id: number
  commonTableId: number
  version?: string
  status: string
  deployedAt?: string
  deployedBy?: string
  notes?: string
  createdAt?: string
}

export interface AdminCommonTableAccess {
  id: number
  commonTableId: number
  accessType: string
  targetType: string
  targetId: string
  roleName?: string
  createdAt?: string
  createdBy?: string
}

const BASE = '/admin/common-tables'

export const adminCommonTableApi = {
  list(): Promise<{ success: boolean; data: AdminCommonTable[] }> {
    return request.get(BASE)
  },

  getById(id: number): Promise<{ success: boolean; data: AdminCommonTable }> {
    return request.get(`${BASE}/${id}`)
  },

  setEnabled(id: number, enabled: boolean): Promise<{ success: boolean; data: AdminCommonTable }> {
    return request.put(`${BASE}/${id}/enabled`, { enabled })
  },

  delete(id: number): Promise<{ success: boolean }> {
    return request.delete(`${BASE}/${id}`)
  },

  listAllDeployments(): Promise<{ success: boolean; data: AdminCommonTableDeployment[] }> {
    return request.get(`${BASE}/deployments`)
  },

  listDeployments(id: number): Promise<{ success: boolean; data: AdminCommonTableDeployment[] }> {
    return request.get(`${BASE}/${id}/deployments`)
  },

  rollback(deploymentId: number, notes?: string): Promise<{ success: boolean; data: AdminCommonTableDeployment }> {
    return request.post(`${BASE}/deployments/${deploymentId}/rollback`, { notes })
  },

  getVersionsByCode(code: string): Promise<{ success: boolean; data: AdminCommonTable[] }> {
    return request.get(`${BASE}/code/${code}/versions`)
  },

  getAccess(id: number): Promise<{ success: boolean; data: AdminCommonTableAccess[] }> {
    return request.get(`${BASE}/${id}/access`)
  },

  addAccess(id: number, roleId: string): Promise<{ success: boolean; data: AdminCommonTableAccess }> {
    return request.post(`${BASE}/${id}/access`, { roleId })
  },

  deleteAccess(id: number, accessId: number): Promise<{ success: boolean }> {
    return request.delete(`${BASE}/${id}/access/${accessId}`)
  }
}
