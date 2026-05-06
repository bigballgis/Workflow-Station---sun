// NOTE: This file is duplicated across admin-center, developer-workstation, user-portal.
// Consider extracting to a shared package. See ISSUE-095.
import axios from 'axios'

/**
 * Authentication API module.
 * Validates: Requirements 5.1, 5.5
 */

const authRequest = axios.create({
  baseURL: '/api/v1/auth',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

/** Per-app keys so DW / Portal / Admin on the same origin do not clobber each other's sessions */
export const TOKEN_KEY = 'ws_dw_access_token'
export const REFRESH_TOKEN_KEY = 'ws_dw_refresh_token'
export const USER_KEY = 'ws_dw_user'
export const USER_ID_KEY = 'ws_dw_user_id'

/**
 * One-time migration from pre-namespaced keys (same keys as other apps, easy to lose session after deploy).
 * Runs at module load; safe if `ws_dw_*` already set.
 */
function migrateLegacyDwAuthStorage(): void {
  try {
    if (typeof localStorage === 'undefined') return
    if (localStorage.getItem(TOKEN_KEY)) return
    const legacyToken = localStorage.getItem('token')
    if (!legacyToken) return
    localStorage.setItem(TOKEN_KEY, legacyToken)
    const rt = localStorage.getItem('refreshToken')
    if (rt) localStorage.setItem(REFRESH_TOKEN_KEY, rt)
    const u = localStorage.getItem('user')
    if (u) localStorage.setItem(USER_KEY, u)
    const uid = localStorage.getItem('userId')
    if (uid) localStorage.setItem(USER_ID_KEY, uid)
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
    localStorage.removeItem('userId')
  } catch {
    /* ignore quota / private mode */
  }
}

migrateLegacyDwAuthStorage()

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserInfo
}

/** Assignment target type */
export type AssignmentTargetType = 'USER' | 'VIRTUAL_GROUP'

/** Role with source information */
export interface RoleWithSource {
  roleCode: string
  roleName: string
  sourceType: AssignmentTargetType
  sourceId: string
  sourceName: string
}

export interface UserInfo {
  userId: string
  username: string
  displayName: string
  email: string
  roles: string[]
  permissions: string[]
  rolesWithSources?: RoleWithSource[]
  language: string
}

export interface TokenResponse {
  accessToken: string
  expiresIn: number
  refreshToken?: string
}

export const login = async (data: LoginRequest): Promise<LoginResponse> => {
  const response = await authRequest.post<LoginResponse>('/login', data)
  return response.data
}

export const exchangeSsoCode = async (code: string, state?: string): Promise<LoginResponse> => {
  const response = await authRequest.post<LoginResponse>('/sso/exchange', { code, state })
  return response.data
}

export const logout = async (): Promise<void> => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    try {
      await authRequest.post('/logout', null, {
        headers: { Authorization: `Bearer ${token}` }
      })
    } catch (e) {
      console.warn('Logout request failed:', e)
    }
  }
}

export const refreshToken = async (refreshToken: string): Promise<TokenResponse> => {
  const response = await authRequest.post<TokenResponse>('/refresh', { refreshToken })
  return response.data
}

export const getCurrentUser = async (): Promise<UserInfo> => {
  const token = localStorage.getItem(TOKEN_KEY)
  const response = await authRequest.get<UserInfo>('/me', {
    headers: { Authorization: `Bearer ${token}` }
  })
  return response.data
}

export const saveTokens = (accessToken: string, refreshToken: string) => {
  localStorage.setItem(TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export const saveUser = (user: UserInfo) => {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export const getStoredUser = (): UserInfo | null => {
  const userStr = localStorage.getItem(USER_KEY)
  if (userStr) {
    try {
      return JSON.parse(userStr)
    } catch {
      return null
    }
  }
  return null
}

// Alias for getStoredUser
export const getUser = getStoredUser

export const clearAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(USER_ID_KEY)
}

export const isAuthenticated = (): boolean => {
  return !!localStorage.getItem(TOKEN_KEY)
}
