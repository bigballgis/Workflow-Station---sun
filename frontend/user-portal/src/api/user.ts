import axios from 'axios'
import { TOKEN_KEY } from './auth'
import { request as portalRequest } from './request'

/** 用户业务单元成员身份 */
export interface UserBusinessUnitMembership {
  id: string
  name: string
  code?: string
  path?: string
}

/** 用户虚拟组成员身份 */
export interface UserVirtualGroupMembership {
  groupId: string
  groupName: string
  groupDescription?: string
  joinedAt: string
}

/** 用户角色 */
export interface UserRole {
  id: string
  name: string
  code: string
  type: string
}

// 创建独立的 axios 实例访问 admin-center（通过 /api/v1/admin 代理）
const adminCenterAxios = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 30000,
  withCredentials: true
})

adminCenterAxios.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
)

/** 用户搜索结果 */
export interface UserOption {
  id: string
  name: string
  username: string
}

/** Admin-center 用户详情（GET /users/{id} 响应体，字段随版本可能扩展） */
interface AdminUserDetailPayload {
  id?: string
  username?: string
  displayName?: string
  fullName?: string
}

// 用户API - 通过 /api/v1/admin 代理访问 admin-center 后端
export const userApi = {
  // 搜索用户（用于转办、委托等场景）- 通过 user-portal 后端代理
  searchUsers: async (keyword: string): Promise<UserOption[]> => {
    try {
      interface SearchUserItem {
        id: string
        displayName?: string
        fullName?: string
        username: string
      }
      const res = await portalRequest.get<{ data?: SearchUserItem[] }>('/tasks/users/search', { params: { keyword } })
      const data = (res as { data?: SearchUserItem[] })?.data ?? (res as unknown)
      const users: SearchUserItem[] = Array.isArray(data) ? data : []
      return users.map((u: SearchUserItem) => ({
        id: u.id,
        name: u.displayName || u.fullName || u.username,
        username: u.username
      }))
    } catch {
      return []
    }
  },

  /**
   * 按用户 ID 解析显示名（与任务详情 assigneeName 数据源一致，供子表等只读场景使用）
   */
  getUserSummary: async (userId: string): Promise<UserOption | null> => {
    const id = String(userId || '').trim()
    if (!id) return null
    try {
      const u = (await adminCenterAxios.get(`/users/${encodeURIComponent(id)}`)) as AdminUserDetailPayload
      if (!u || (!u.id && !u.username)) return null
      const name = String(u.displayName || u.fullName || u.username || u.id || id)
      return { id: String(u.id ?? id), name, username: String(u.username || '') }
    } catch {
      return null
    }
  },

  // 获取用户业务单元
  getBusinessUnits: (userId: string): Promise<UserBusinessUnitMembership[]> =>
    adminCenterAxios.get(`/users/${userId}/business-units`),
  
  // 获取用户虚拟组
  getVirtualGroups: (userId: string): Promise<UserVirtualGroupMembership[]> =>
    adminCenterAxios.get(`/users/${userId}/virtual-groups`),

  // 获取用户角色（通过虚拟组）
  getRoles: (userId: string): Promise<UserRole[]> =>
    adminCenterAxios.get(`/users/${userId}/roles`),
}
