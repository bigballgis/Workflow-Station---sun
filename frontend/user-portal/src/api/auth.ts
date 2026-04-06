// NOTE: This file is duplicated across admin-center, developer-workstation, user-portal.
// Consider extracting to a shared package. See ISSUE-095.
import axios from 'axios'

/**
 * Authentication API module.
 * Validates: Requirements 5.1, 5.5
 */

const authRequest = axios.create({
  baseURL: '/api/portal/auth',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

export interface WorkspaceContextOption {
  businessUnitId: string
  roleId: string
  businessUnitName?: string
  roleCode?: string
  roleName?: string
}

export interface LoginRequest {
  username: string
  password: string
  workspaceBusinessUnitId?: string
  workspaceRoleId?: string
}

export interface LoginResponse {
  accessToken?: string
  refreshToken?: string
  expiresIn?: number
  user?: UserInfo
  loginErrorCode?: string
  workspaceContexts?: WorkspaceContextOption[]
  /** 失败时后端返回的说明文案 */
  message?: string
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
  activeBusinessUnitId?: string
  activeBusinessUnitName?: string
  activeRoleId?: string
  activeRoleName?: string
  workspaceSwitcherVisible?: boolean
  /** FULL | PERMISSION_SELF_SERVICE_ONLY */
  portalAccessMode?: string
}

export interface TokenResponse {
  accessToken: string
  expiresIn: number
  /** 刷新轮换时返回；存在则应写入 localStorage */
  refreshToken?: string
}

export const login = async (data: LoginRequest): Promise<LoginResponse> => {
  // #region agent log
  fetch('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '97dc8c' },
    body: JSON.stringify({
      sessionId: '97dc8c',
      runId: 'run-login',
      hypothesisId: 'L1-auth-request',
      location: 'auth.ts:login',
      message: 'sending /auth/login request',
      data: {
        hasWorkspaceBusinessUnitId: !!data.workspaceBusinessUnitId,
        hasWorkspaceRoleId: !!data.workspaceRoleId,
        usernameLen: data.username?.length ?? 0
      },
      timestamp: Date.now()
    })
  }).catch(() => {})
  // #endregion
  try {
    const response = await authRequest.post<LoginResponse>('/login', data)
    // #region agent log
    fetch('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '97dc8c' },
      body: JSON.stringify({
        sessionId: '97dc8c',
        runId: 'run-login',
        hypothesisId: 'L2-auth-response',
        location: 'auth.ts:login',
        message: 'received /auth/login response',
        data: {
          hasAccessToken: !!response.data?.accessToken,
          hasRefreshToken: !!response.data?.refreshToken,
          hasUser: !!response.data?.user
        },
        timestamp: Date.now()
      })
    }).catch(() => {})
    // #endregion
    return response.data
  } catch (error: unknown) {
    const ax = error as { response?: { status?: number; data?: unknown }; message?: string }
    const body = ax.response?.data as Record<string, unknown> | undefined
    // #region agent log
    fetch('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': '97dc8c' },
      body: JSON.stringify({
        sessionId: '97dc8c',
        runId: 'run-login',
        hypothesisId: 'L3-auth-error',
        location: 'auth.ts:login',
        message: 'login request failed',
        data: {
          httpStatus: ax.response?.status,
          errMsg: typeof ax.message === 'string' ? ax.message : '',
          bodyKeys: body ? Object.keys(body) : []
        },
        timestamp: Date.now()
      })
    }).catch(() => {})
    // #endregion
    throw error
  }
}

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

export const listWorkspaceContexts = async (): Promise<WorkspaceContextOption[]> => {
  const token = localStorage.getItem('token')
  const response = await authRequest.get<WorkspaceContextOption[]>('/workspace-contexts', {
    headers: { Authorization: `Bearer ${token}` }
  })
  return response.data
}

export const switchWorkspace = async (businessUnitId: string, roleId: string): Promise<LoginResponse> => {
  const token = localStorage.getItem('token')
  const response = await authRequest.post<LoginResponse>(
    '/switch-workspace',
    { businessUnitId, roleId },
    { headers: { Authorization: `Bearer ${token}` } }
  )
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

/**
 * 与 PortalLayout 同步逻辑一致：库中已有工作台（UBR）而 /me 仍为 PERMISSION_SELF_SERVICE_ONLY 时，
 * 本地视为 FULL，避免路由守卫在布局挂载前把 /tasks 等重定向到 /permissions。
 * 任务等接口仍须 JWT 非自助模式；依赖 reconcilePortalWorkspaceSession 换发令牌。
 */
export function applyWorkspaceAwarePortalAccess(
  u: UserInfo,
  hasWorkspaceContexts: boolean
): UserInfo {
  if (hasWorkspaceContexts && u.portalAccessMode === 'PERMISSION_SELF_SERVICE_ONLY') {
    return { ...u, portalAccessMode: 'FULL' }
  }
  return u
}

export const clearAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem('userId')
}

export const isAuthenticated = (): boolean => {
  return !!localStorage.getItem(TOKEN_KEY)
}

/**
 * 先以「无 UBR」登录时 JWT 为 PERMISSION_SELF_SERVICE_ONLY；管理员补录 UBR 后仍用旧 token，
 * Security 仍按 JWT 拦截非白名单接口。若库中已有工作台，用第一条 UBR 调用 switch-workspace 换发 FULL token。
 */
export async function reconcilePortalWorkspaceSession(): Promise<boolean> {
  if (!localStorage.getItem(TOKEN_KEY)) {
    return false
  }
  const stored = getStoredUser()
  const needReconcile =
    stored?.portalAccessMode === 'PERMISSION_SELF_SERVICE_ONLY' ||
    !stored?.activeBusinessUnitId?.trim() ||
    !stored?.activeRoleId?.trim()
  if (!needReconcile) return false
  let contexts: WorkspaceContextOption[] = []
  try {
    contexts = await listWorkspaceContexts()
  } catch {
    return false
  }
  if (contexts.length === 0) return false
  const c = contexts[0]!
  try {
    const resp = await switchWorkspace(c.businessUnitId, c.roleId)
    if (resp.accessToken && resp.refreshToken && resp.user) {
      saveTokens(resp.accessToken, resp.refreshToken)
      saveUser(resp.user)
      localStorage.setItem('userId', resp.user.userId)
      return true
    }
  } catch {
    // 保持旧会话
  }
  return false
}
