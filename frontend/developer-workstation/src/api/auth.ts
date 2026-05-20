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
  withCredentials: true,
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
    // Only migrate user profile data (not tokens — tokens are httpOnly cookies now)
    const u = localStorage.getItem('user')
    if (u && !localStorage.getItem(USER_KEY)) {
      localStorage.setItem(USER_KEY, u)
    }
    const uid = localStorage.getItem('userId')
    if (uid && !localStorage.getItem(USER_ID_KEY)) {
      localStorage.setItem(USER_ID_KEY, uid)
    }
    // Clean up legacy token storage (migrated to httpOnly cookies)
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
  try {
    await authRequest.post('/logout')
  } catch (e) {
    console.warn('Logout request failed:', e)
  }
}

export const refreshToken = async (): Promise<TokenResponse> => {
  const response = await authRequest.post<TokenResponse>('/refresh')
  return response.data
}

export const getCurrentUser = async (): Promise<UserInfo> => {
  const response = await authRequest.get<UserInfo>('/me')
  return response.data
}

// saveTokens is a no-op: access/refresh tokens are now managed via httpOnly cookies
export const saveTokens = (_accessToken?: string, _refreshToken?: string) => {
  // no-op: cookies are auto-set by the backend
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
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(USER_ID_KEY)
}

// isAuthenticated now returns true; actual auth is enforced by cookie presence on API calls
export const isAuthenticated = (): boolean => {
  return true
}
