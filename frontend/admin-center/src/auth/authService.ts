/**
 * Auth service with defensive refresh.
 * Token = identity credential. System access = Token + Role Gate.
 */

import axios from 'axios'
import { tokenStorage, type UserInfo } from './tokenStorage'

const authRequest = axios.create({
  baseURL: '/api/v1/admin/auth',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

authRequest.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 400 && err.response?.data?.message) {
      err.message = err.response.data.message
    }
    return Promise.reject(err)
  }
)

export interface LoginCredentials {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  user: UserInfo
}

export const authService = {
  async login(credentials: LoginCredentials): Promise<LoginResult> {
    const { data } = await authRequest.post<LoginResult>('/login', credentials)
    return data
  },

  async logout(): Promise<void> {
    const token = tokenStorage.getAccessToken()
    if (token) {
      try {
        await authRequest.post('/logout', null, {
          headers: { Authorization: `Bearer ${token}` }
        })
      } catch (e) {
        console.warn('Logout request failed:', e)
      }
    }
    tokenStorage.clear()
  },

  /**
   * Defensive refresh: validates that backend returns a real access token,
   * not the refresh token (workaround for Admin Center backend bug).
   * Returns new access token or null on failure.
   */
  async refreshToken(): Promise<string | null> {
    const refresh = tokenStorage.getRefreshToken()
    if (!refresh) return null

    try {
      const { data } = await authRequest.post<{ accessToken?: string; refreshToken?: string }>('/refresh', {
        refreshToken: refresh
      })
      const newAccess = data?.accessToken
      if (!newAccess || newAccess === refresh) {
        tokenStorage.clear()
        return null
      }
      tokenStorage.setTokens(newAccess, data.refreshToken ?? refresh)
      return newAccess
    } catch {
      tokenStorage.clear()
      return null
    }
  },

  async getCurrentUser(): Promise<UserInfo> {
    const token = tokenStorage.getAccessToken()
    const { data } = await authRequest.get<UserInfo>('/me', {
      headers: { Authorization: `Bearer ${token}` }
    })
    return data
  },

  async validateToken(): Promise<boolean> {
    const token = tokenStorage.getAccessToken()
    if (!token) return false
    try {
      const { data } = await authRequest.get<boolean>('/validate', {
        headers: { Authorization: `Bearer ${token}` }
      })
      return data
    } catch {
      return false
    }
  },

  checkAccess(requiredRoles: string[]): boolean {
    const user = tokenStorage.getUser()
    if (!user?.roles?.length) return false
    return requiredRoles.some((r) => user.roles.includes(r))
  }
}
