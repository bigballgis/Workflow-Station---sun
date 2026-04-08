import axios from 'axios'
import { TOKEN_KEY } from './auth'

const workstationAuthAxios = axios.create({
  baseURL: '/api/v1/auth',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})
workstationAuthAxios.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
workstationAuthAxios.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
)

/**
 * User API module for developer-workstation
 * Provides access to user business units, virtual groups, and roles
 */

const adminCenterAxios = axios.create({
  baseURL: '/api/v1/admin',
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
  /** Studio header passes DEVELOPER to show dev roles/virtual groups only; omit for full list */
  getBusinessUnits: (
    userId: string,
    profileContext?: 'PORTAL' | 'ADMIN' | 'DEVELOPER'
  ): Promise<UserBusinessUnitMembership[]> =>
    adminCenterAxios.get(`/users/${userId}/business-units`, {
      params: profileContext ? { profileContext } : undefined
    }),

  getVirtualGroups: (
    userId: string,
    profileContext?: 'PORTAL' | 'ADMIN' | 'DEVELOPER'
  ): Promise<UserVirtualGroupMembership[]> =>
    adminCenterAxios.get(`/users/${userId}/virtual-groups`, {
      params: profileContext ? { profileContext } : undefined
    }),

  getRoles: (userId: string, profileContext?: 'PORTAL' | 'ADMIN' | 'DEVELOPER'): Promise<UserRole[]> =>
    adminCenterAxios.get(`/users/${userId}/roles`, {
      params: profileContext ? { profileContext } : undefined
    }),

  /** Change password (developer-workstation auth service) */
  changePassword: (data: { oldPassword: string; newPassword: string }): Promise<void> =>
    workstationAuthAxios.post('/change-password', data)
}
