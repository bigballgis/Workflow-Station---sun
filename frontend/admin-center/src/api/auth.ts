// NOTE: This file is duplicated across admin-center, developer-workstation, user-portal.
// Consider extracting to a shared package. See ISSUE-095.
import axios from 'axios'

/**
 * Authentication API module.
 * Validates: Requirements 5.1, 5.5
 */

// Create a separate axios instance for auth to avoid circular dependencies
// 必须与 Kong 中 admin-center-auth 路由一致：/api/v1/admin/auth/*
// 若使用 /api/v1/auth/*，会被转发到 developer-workstation，导致管理端登录 400
const authRequest = axios.create({
  baseURL: '/api/v1/admin/auth',
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

/** 分配目标类型 */
export type AssignmentTargetType = 'USER' | 'VIRTUAL_GROUP'

/** 角色及来源信息 */
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

export interface RefreshRequest {
  refreshToken: string
}

/**
 * Login with username and password.
 */
export const login = async (data: LoginRequest): Promise<LoginResponse> => {
  const response = await authRequest.post<LoginResponse>('/login', data)
  return response.data
}

/**
 * Logout and invalidate token.
 */
export const logout = async (): Promise<void> => {
  const token = localStorage.getItem('token')
  if (token) {
    try {
      await authRequest.post('/logout', null, {
        headers: { Authorization: `Bearer ${token}` }
      })
    } catch {
      // Ignore logout errors, still clear local storage
    }
  }
}

/**
 * Refresh access token using refresh token.
 */
export const refreshToken = async (refreshToken: string): Promise<LoginResponse> => {
  const response = await authRequest.post<LoginResponse>('/refresh', { refreshToken })
  return response.data
}

/**
 * Get current user info.
 */
export const getCurrentUser = async (): Promise<UserInfo> => {
  const token = localStorage.getItem('token')
  const response = await authRequest.get<UserInfo>('/me', {
    headers: { Authorization: `Bearer ${token}` }
  })
  return response.data
}

/** 修改密码（须走 /api/v1/admin/auth，勿使用 /api/v1/auth，避免被网关转发到设计器服务） */
export const changePassword = async (data: { oldPassword: string; newPassword: string }): Promise<void> => {
  const token = localStorage.getItem(TOKEN_KEY)
  await authRequest.post('/change-password', data, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

/**
 * Validate token.
 */
export const validateToken = async (): Promise<boolean> => {
  const token = localStorage.getItem('token')
  if (!token) return false
  
  try {
    const response = await authRequest.get<boolean>('/validate', {
      headers: { Authorization: `Bearer ${token}` }
    })
    return response.data
  } catch {
    return false
  }
}

// Token storage helpers
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
  localStorage.removeItem('username')
}

export const isAuthenticated = (): boolean => {
  return !!localStorage.getItem(TOKEN_KEY)
}
