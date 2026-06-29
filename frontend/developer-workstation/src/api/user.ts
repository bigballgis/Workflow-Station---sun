import axios from 'axios'
import { TOKEN_KEY, getUser } from './auth'

const workstationAuthAxios = axios.create({
  baseURL: '/api/v1/auth',
  timeout: 30000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
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
  timeout: 30000,
  withCredentials: true
})

adminCenterAxios.interceptors.request.use(config => {
  const user = getUser()
  if (user?.userId) {
    config.headers['X-User-Id'] = user.userId
  }
  return config
})

adminCenterAxios.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
)

/** Platform user row for initiator / assignee pickers */
export interface PlatformUserOption {
  id: string
  username: string
  displayName: string
  email: string
}

interface AdminUserListRow {
  id: string
  username?: string
  fullName?: string
  displayName?: string
  email?: string
}

interface AdminUserPage {
  content?: AdminUserListRow[]
}

function mapPlatformUser(u: AdminUserListRow): PlatformUserOption {
  return {
    id: u.id,
    username: u.username ?? '',
    displayName: u.displayName || u.fullName || u.username || u.id,
    email: u.email ?? ''
  }
}

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
    workstationAuthAxios.post('/change-password', data),

  /** Search platform users (name / username / email) for System Initiator picker */
  searchUsers: async (keyword?: string): Promise<PlatformUserOption[]> => {
    const params: Record<string, string | number> = { page: 0, size: 50 }
    const q = keyword?.trim()
    if (q) params.keyword = q
    const res = (await adminCenterAxios.get('/users', { params })) as AdminUserPage
    return (res.content ?? []).map(mapPlatformUser)
  },

  getUserById: async (userId: string): Promise<PlatformUserOption | null> => {
    const id = userId.trim()
    if (!id) return null
    try {
      const u = (await adminCenterAxios.get(`/users/${encodeURIComponent(id)}`)) as AdminUserListRow
      if (!u?.id) return null
      return mapPlatformUser(u)
    } catch {
      return null
    }
  }
}
