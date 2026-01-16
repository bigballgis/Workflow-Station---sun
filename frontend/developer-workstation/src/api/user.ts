import axios from 'axios'
import { TOKEN_KEY } from './auth'

/**
 * User API module for developer-workstation
 * Provides access to user business units, virtual groups, and roles
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

export const userApi = {
  /** 获取用户业务单元成员身份 */
  getBusinessUnits: (userId: string): Promise<UserBusinessUnitMembership[]> =>
    adminCenterAxios.get(`/users/${userId}/business-units`),

  /** 获取用户虚拟组成员身份 */
  getVirtualGroups: (userId: string): Promise<UserVirtualGroupMembership[]> =>
    adminCenterAxios.get(`/users/${userId}/virtual-groups`),

  /** 获取用户角色（通过虚拟组获取） */
  getRoles: (userId: string): Promise<UserRole[]> =>
    adminCenterAxios.get(`/users/${userId}/roles`),

  /** 修改密码 */
  changePassword: (data: { oldPassword: string; newPassword: string }): Promise<void> =>
    adminCenterAxios.post('/auth/change-password', data)
}
