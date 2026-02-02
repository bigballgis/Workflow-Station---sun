/**
 * Centralized token and user storage.
 * All auth-related localStorage access MUST go through this module.
 */

export const TOKEN_KEY = 'token'
export const REFRESH_TOKEN_KEY = 'refreshToken'
export const USER_KEY = 'user'
export const USER_ID_KEY = 'userId'

export interface UserInfo {
  userId: string
  username: string
  displayName: string
  email: string
  roles: string[]
  permissions: string[]
  rolesWithSources?: { roleCode: string; roleName: string; sourceType: string; sourceId: string; sourceName: string }[]
  language: string
}

export const tokenStorage = {
  getAccessToken: (): string | null => localStorage.getItem(TOKEN_KEY),
  getRefreshToken: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),
  setTokens: (access: string, refresh: string): void => {
    localStorage.setItem(TOKEN_KEY, access)
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  },
  getUser: (): UserInfo | null => {
    const s = localStorage.getItem(USER_KEY)
    if (!s) return null
    try {
      return JSON.parse(s) as UserInfo
    } catch {
      return null
    }
  },
  setUser: (user: UserInfo): void => {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    if (user?.userId) {
      localStorage.setItem(USER_ID_KEY, user.userId)
    }
  },
  getUserId: (): string | null => localStorage.getItem(USER_ID_KEY),
  clear: (): void => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(USER_ID_KEY)
  }
}
