import axios from 'axios'
import { TOKEN_KEY, getUser } from './auth'

/**
 * Admin Center API module for developer-workstation
 * Provides access to departments and virtual groups for assignee configuration
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
  
  // 添加 X-User-Id 请求头，用于后端权限检查
  const user = getUser()
  if (user && user.userId) {
    config.headers['X-User-Id'] = user.userId
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

export const adminCenterApi = {
  /** 获取部门树 */
  getDepartmentTree: async (): Promise<DepartmentTree[]> => {
    const response = await adminCenterAxios.get('/departments/tree')
    // 响应可能是数组或包含数组的对象
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 搜索部门 */
  searchDepartments: async (keyword: string): Promise<DepartmentTree[]> => {
    const response = await adminCenterAxios.get('/departments/search', { params: { keyword } })
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 获取虚拟组列表 */
  getVirtualGroups: async (type?: string, status?: string): Promise<VirtualGroupInfo[]> => {
    const response = await adminCenterAxios.get('/virtual-groups', { params: { type, status } })
    return Array.isArray(response) ? response : (response as any)?.data || []
  },

  /** 获取虚拟组详情 */
  getVirtualGroup: (groupId: string) =>
    adminCenterAxios.get<any, VirtualGroupInfo>(`/virtual-groups/${groupId}`)
}
