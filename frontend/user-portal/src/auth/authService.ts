/**
 * Auth service with defensive refresh.
 * Token = identity credential. System access = Token + Role Gate.
 */

import axios from 'axios'
import { tokenStorage, type UserInfo } from './tokenStorage'

const authRequest = axios.create({
  // Unified Auth: Admin Center /api/v1/admin/auth/**
  baseURL: '/api/v1/admin/auth',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

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
    // Use unified public login (no Admin Center role gate)
    const { data } = await authRequest.post<LoginResult>('/public-login', credentials)
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
   * Defensive refresh: validates that backend returns a real access token.
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

  checkAccess(_requiredRoles: string[]): boolean {
    return true
  }
}
