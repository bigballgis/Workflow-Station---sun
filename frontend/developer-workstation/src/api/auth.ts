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
}

export const login = async (data: LoginRequest): Promise<LoginResponse> => {
  const response = await authRequest.post<LoginResponse>('/login', data)
  return response.data
}

export const logout = async (): Promise<void> => {
  const token = localStorage.getItem('token')
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
  const token = localStorage.getItem('token')
  const response = await authRequest.get<UserInfo>('/me', {
    headers: { Authorization: `Bearer ${token}` }
  })
  return response.data
}

export const TOKEN_KEY = 'token'
export const REFRESH_TOKEN_KEY = 'refreshToken'
export const USER_KEY = 'user'

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
  localStorage.removeItem('userId')
}

export const isAuthenticated = (): boolean => {
  return !!localStorage.getItem(TOKEN_KEY)
}
