/**
 * SSO 登录 API 模块
 *
 * 类型定义 + fetch 封装。替代 App.vue 中内联的 fetch() 调用。
 */

// ==================== 类型定义 ====================

/** SSO 登录请求 */
export interface LoginRequest {
  username: string
  password: string
  clientId: string
  redirectUri: string
  state?: string
}

/** 后端成功响应 */
export interface LoginResponse {
  authorizationCode: string
  state?: string
  redirectUri: string
}

/** 后端错误响应体（多种可能格式） */
export interface LoginErrorBody {
  message?: string
  error?:
    | string
    | {
        message?: string
        detail?: string
      }
}

/** 登录结果（联合类型，避免 try/catch 嵌套） */
export type LoginResult =
  | { ok: true; data: LoginResponse }
  | { ok: false; error: string }

// ==================== API 调用 ====================

const BASE = '/api/v1/admin/sso/login'

/**
 * 发送 SSO 登录请求。
 * 返回 LoginResult 统一处理成功/失败，调用方无需关心 HTTP 细节。
 */
export async function login(req: LoginRequest): Promise<LoginResult> {
  let res: Response
  try {
    res = await fetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: req.username,
        password: req.password,
        clientId: req.clientId,
        redirectUri: req.redirectUri,
        state: req.state || undefined,
      }),
    })
  } catch {
    return { ok: false, error: 'Network error.' }
  }

  if (!res.ok) {
    return { ok: false, error: await parseErrorResponse(res) }
  }

  const data: LoginResponse = await res.json()
  if (!data.authorizationCode || !data.redirectUri) {
    return { ok: false, error: 'Invalid login response.' }
  }

  return { ok: true, data }
}

// ==================== 内部工具 ====================

async function parseErrorResponse(res: Response): Promise<string> {
  let detail = ''
  try {
    const body: LoginErrorBody = await res.json()
    const msg =
      body?.message ??
      (typeof body?.error === 'object' ? body.error?.message ?? body.error?.detail : null) ??
      (typeof body?.error === 'string' ? body.error : null)
    if (typeof msg === 'string' && msg.trim()) detail = msg.trim()
  } catch {
    /* body 不是 JSON */
  }
  if (detail) return detail
  if (res.status >= 500) {
    return `Server error (${res.status}). Is Kong/admin-center healthy?`
  }
  return 'Invalid username or password, or SSO redirect_uri was rejected.'
}
