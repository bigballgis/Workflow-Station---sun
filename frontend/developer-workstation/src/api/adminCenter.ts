import axios from 'axios'
import { TOKEN_KEY } from './auth'

/**
 * Admin Center API module for developer-workstation
 * Provides access to departments, virtual groups, business units, and roles for assignee configuration
 */

const adminCenterAxios = axios.create({
  baseURL: '/api/admin-center',
  timeout: 30000
})

adminCenterAxios.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

adminCenterAxios.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
)

/** 部门树节点 */
export interface DepartmentTree {
  id: string
  name: string
  code?: string
  parentId?: string
  level?: number
  path?: string
  managerId?: string
  managerName?: string
  secondaryManagerId?: string
  secondaryManagerName?: string
  children?: DepartmentTree[]
}

/** 虚拟组信息 */
export interface VirtualGroupInfo {
  id: string
  name: string
  description?: string
  type?: string
  status?: string
  memberCount?: number
}

/** 业务单元信息 */
export interface BusinessUnitInfo {
  id: string
  name: string
  code?: string
  parentId?: string
  level?: number
  path?: string
  status?: string
  children?: BusinessUnitInfo[]
}

/** 角色信息 */
export interface RoleInfo {
  id: string
  name: string
  code: string
  type: 'BU_BOUNDED' | 'BU_UNBOUNDED' | 'ADMIN' | 'DEVELOPER'
  description?: string
  status?: string
}

export const adminCenterApi = {
  // ==================== 部门相关 ====================
  
  /** 获取部门树 */
  getDepartmentTree: async (): Promise<DepartmentTree[]> => {
    const response = await adminCenterAxios.get('/departments/tree')
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 搜索部门 */
  searchDepartments: async (keyword: string): Promise<DepartmentTree[]> => {
    const response = await adminCenterAxios.get('/departments/search', { params: { keyword } })
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  // ==================== 虚拟组相关 ====================
  
  /** 获取虚拟组列表 */
  getVirtualGroups: async (type?: string, status?: string): Promise<VirtualGroupInfo[]> => {
    const response = await adminCenterAxios.get('/virtual-groups', { params: { type, status } })
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 获取虚拟组详情 */
  getVirtualGroup: (groupId: string) =>
    adminCenterAxios.get<any, VirtualGroupInfo>(`/virtual-groups/${groupId}`),

  // ==================== 业务单元相关 ====================
  
  /** 获取业务单元树 */
  getBusinessUnitTree: async (): Promise<BusinessUnitInfo[]> => {
    const response = await adminCenterAxios.get('/business-units/tree')
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 获取业务单元列表 */
  getBusinessUnits: async (): Promise<BusinessUnitInfo[]> => {
    const response = await adminCenterAxios.get('/business-units')
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 获取业务单元详情 */
  getBusinessUnit: (unitId: string) =>
    adminCenterAxios.get<any, BusinessUnitInfo>(`/business-units/${unitId}`),

  /** 获取业务单元的准入角色列表 */
  getBusinessUnitEligibleRoles: async (unitId: string): Promise<RoleInfo[]> => {
    const response = await adminCenterAxios.get(`/business-units/${unitId}/roles`)
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  // ==================== 角色相关 ====================
  
  /** 获取所有角色 */
  getRoles: async (type?: string): Promise<RoleInfo[]> => {
    const response = await adminCenterAxios.get('/roles', { params: { type } })
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 获取所有BU绑定型角色 */
  getBuBoundedRoles: async (): Promise<RoleInfo[]> => {
    const response = await adminCenterAxios.get('/task-assignment/roles/bu-bounded')
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 获取所有BU无关型角色 */
  getBuUnboundedRoles: async (): Promise<RoleInfo[]> => {
    const response = await adminCenterAxios.get('/task-assignment/roles/bu-unbounded')
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 获取角色详情 */
  getRole: (roleId: string) =>
    adminCenterAxios.get<any, RoleInfo>(`/roles/${roleId}`)
}
