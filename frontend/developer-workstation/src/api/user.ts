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

/** User business unit membership */
export interface UserBusinessUnitMembership {
  id: string
  name: string
  code?: string
  path?: string
}

/** User virtual group membership */
export interface UserVirtualGroupMembership {
  groupId: string
  groupName: string
  groupDescription?: string
  joinedAt: string
}

/** User role */
export interface UserRole {
  id: string
  name: string
  code: string
  type: string
}

export const userApi = {
  /** Get user business unit memberships */
  getBusinessUnits: (userId: string): Promise<UserBusinessUnitMembership[]> =>
    adminCenterAxios.get(`/users/${userId}/business-units`),

  /** Get user virtual group memberships */
  getVirtualGroups: (userId: string): Promise<UserVirtualGroupMembership[]> =>
    adminCenterAxios.get(`/users/${userId}/virtual-groups`),

  /** Get user roles (via virtual groups) */
  getRoles: (userId: string): Promise<UserRole[]> =>
    adminCenterAxios.get(`/users/${userId}/roles`),

  /** Change password */
  changePassword: (data: { oldPassword: string; newPassword: string }): Promise<void> =>
    adminCenterAxios.post('/auth/change-password', data)
}
