/**
 * Authentication API module.
 * Uses tokenStorage and authService - no direct localStorage access.
 */

import { authService } from '@/auth/authService'
import { tokenStorage, TOKEN_KEY, REFRESH_TOKEN_KEY, USER_KEY } from '@/auth/tokenStorage'
import type { UserInfo } from '@/auth/tokenStorage'

export type { UserInfo }

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

export type AssignmentTargetType = 'USER' | 'VIRTUAL_GROUP'

export interface RoleWithSource {
  roleCode: string
  roleName: string
  sourceType: AssignmentTargetType
  sourceId: string
  sourceName: string
}

export interface TokenResponse {
  accessToken: string
  expiresIn: number
}

export const login = async (data: LoginRequest): Promise<LoginResponse> => {
  const result = await authService.login(data)
  return {
    ...result,
    expiresIn: 86400
  }
}

export const logout = async (): Promise<void> => {
  await authService.logout()
}

export const refreshToken = async (_refreshTokenParam?: string): Promise<TokenResponse> => {
  const newAccess = await authService.refreshToken()
  if (!newAccess) throw new Error('Token refresh failed')
  return {
    accessToken: newAccess,
    expiresIn: 86400
  }
}

export const getCurrentUser = async (): Promise<UserInfo> => {
  return authService.getCurrentUser()
}

export const validateToken = async (): Promise<boolean> => {
  return authService.validateToken()
}

export { TOKEN_KEY, REFRESH_TOKEN_KEY, USER_KEY }

export const saveTokens = (accessToken: string, refreshTokenParam: string) => {
  tokenStorage.setTokens(accessToken, refreshTokenParam)
}

export const saveUser = (user: UserInfo) => {
  tokenStorage.setUser(user)
}

export const getStoredUser = (): UserInfo | null => tokenStorage.getUser()
export const getUser = getStoredUser

export const clearAuth = () => {
  tokenStorage.clear()
}

export const isAuthenticated = (): boolean => !!tokenStorage.getAccessToken()
