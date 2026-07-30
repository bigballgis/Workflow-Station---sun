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
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

/** Per-app keys so DW / Portal / Admin on the same origin do not clobber each other's sessions */
export const TOKEN_KEY = 'ws_up_access_token'
export const REFRESH_TOKEN_KEY = 'ws_up_refresh_token'
export const USER_KEY = 'ws_up_user'
export const USER_ID_KEY = 'ws_up_user_id'

function migrateLegacyPortalAuthStorage(): void {
  // With httpOnly cookies, token migration from localStorage is no longer needed.
  // Only user profile data (USER_KEY, USER_ID_KEY) is kept in localStorage.
}

migrateLegacyPortalAuthStorage()

/** SSO exchange：不用带 token，单独走同 base 的 POST */
export async function exchangeSsoCode(payload: {
  code: string
  state?: string
  workspaceBusinessUnitId?: string
  workspaceRoleId?: string
}): Promise<LoginResponse> {
  const response = await authRequest.post<LoginResponse>('/sso/exchange', payload)
  return response.data
}

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
  hasAvatar: boolean
}

export const AUTH_BASE_URL = '/api/portal/auth'

export interface TokenResponse {
  accessToken: string
  expiresIn: number
  /** 刷新轮换时返回；存在则应写入 localStorage */
  refreshToken?: string
}

export const login = async (data: LoginRequest): Promise<LoginResponse> => {
  try {
    const response = await authRequest.post<LoginResponse>('/login', data)
    return response.data
  } catch (error: unknown) {
    const ax = error as { response?: { status?: number; data?: unknown }; message?: string }
    const body = ax.response?.data as Record<string, unknown> | undefined
    throw error
  }
}

export const logout = async (): Promise<void> => {
  try {
    await authRequest.post('/logout')
  } catch {
    // Ignore logout errors, still clear local storage
  }
}

export const refreshToken = async (refreshToken?: string): Promise<TokenResponse> => {
  const response = await authRequest.post<TokenResponse>('/refresh', refreshToken ? { refreshToken } : {})
  return response.data
}

export const getCurrentUser = async (): Promise<UserInfo> => {
  const response = await authRequest.get<UserInfo>('/me')
  return response.data
}

export const listWorkspaceContexts = async (): Promise<WorkspaceContextOption[]> => {
  const response = await authRequest.get<WorkspaceContextOption[]>('/workspace-contexts')
  return response.data
}

export const switchWorkspace = async (businessUnitId: string, roleId: string): Promise<LoginResponse> => {
  const response = await authRequest.post<LoginResponse>(
    '/switch-workspace',
    { businessUnitId, roleId }
  )
  return response.data
}

export const saveTokens = (_accessToken: string, _refreshToken: string) => {
  // Tokens are now httpOnly cookies — no localStorage storage needed
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
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(USER_ID_KEY)
}

export const isAuthenticated = (): boolean => {
  // Auth state is verified by /me because the real token is an httpOnly cookie.
  return !!getStoredUser()
}
export async function verifyPortalSession(): Promise<UserInfo> {
  const fresh = await getCurrentUser()
  saveUser(fresh)
  localStorage.setItem(USER_ID_KEY, fresh.userId)
  await reconcilePortalWorkspaceSession()
  const current = getStoredUser() || fresh
  let contexts: WorkspaceContextOption[] = []
  try {
    contexts = await listWorkspaceContexts()
  } catch {
    contexts = []
  }
  const merged = applyWorkspaceAwarePortalAccess(current, contexts.length > 0)
  saveUser(merged)
  localStorage.setItem(USER_ID_KEY, merged.userId)
  return merged
}

/**
 * 先以「无 UBR」登录时 JWT 为 PERMISSION_SELF_SERVICE_ONLY；管理员补录 UBR 后仍用旧 token，
 * Security 仍按 JWT 拦截非白名单接口。若库中已有工作台，用第一条 UBR 调用 switch-workspace 换发 FULL token。
 */
export async function reconcilePortalWorkspaceSession(): Promise<boolean> {
  const stored = getStoredUser()
  if (!stored) return false
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
      localStorage.setItem(USER_ID_KEY, resp.user.userId)
      return true
    }
  } catch {
    // 保持旧会话
  }
  return false
}
