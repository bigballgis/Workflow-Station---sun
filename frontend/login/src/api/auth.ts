/**
 * SSO 登录 API 模块
 *
 * 类型定义 + fetch 封装。替代 App.vue 中内联的 fetch() 调用。
 */

import { AppErrorCode, type StructuredError } from '@/types/errors'

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
  | { ok: false; error: StructuredError }

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
    return { ok: false, error: { code: AppErrorCode.LOGIN_NETWORK_ERROR } }
  }

  if (!res.ok) {
    return { ok: false, error: await parseErrorResponse(res) }
  }

  const data: LoginResponse = await res.json()
  if (!data.authorizationCode || !data.redirectUri) {
    return { ok: false, error: { code: AppErrorCode.LOGIN_INVALID_RESPONSE } }
  }

  return { ok: true, data }
}

/** DSP 免密请求 */
export interface PasswordlessRequest {
  clientId: string
  redirectUri: string
  state?: string
  /** 浏览器侧 AMToken（mock 验证可经 ?am_token= 注入；真实环境由 DSP/网关注入 cookie/header） */
  amToken?: string
}

const PASSWORDLESS_BASE = '/api/v1/admin/sso/passwordless'

/**
 * DSP 免密登录。后端返回统一 ApiResponse 包装（{success,data,error}），此处解包为 LoginResult。
 * 与 /login 的裸响应不同：成功取 body.data，失败取 body.error.message。
 */
export async function passwordlessLogin(req: PasswordlessRequest): Promise<LoginResult> {
  let res: Response
  try {
    res = await fetch(PASSWORDLESS_BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        clientId: req.clientId,
        redirectUri: req.redirectUri,
        state: req.state || undefined,
        amToken: req.amToken || undefined,
      }),
    })
  } catch {
    return { ok: false, error: { code: AppErrorCode.LOGIN_NETWORK_ERROR } }
  }

  let body: ApiResponseEnvelope<LoginResponse> | null = null
  try {
    body = (await res.json()) as ApiResponseEnvelope<LoginResponse>
  } catch {
    /* 非 JSON 响应 */
  }

  if (res.status === 503) {
    return { ok: false, error: { code: AppErrorCode.DSP_DISABLED } }
  }
  if (!res.ok || !body || body.success !== true || !body.data) {
    const detail = body?.error?.message ?? ''
    return { ok: false, error: { code: AppErrorCode.DSP_FAILED, details: { detail } } }
  }

  const data = body.data
  if (!data.authorizationCode || !data.redirectUri) {
    return { ok: false, error: { code: AppErrorCode.LOGIN_INVALID_RESPONSE } }
  }
  return { ok: true, data }
}

/** platform-common ApiResponse<T> 的前端镜像（仅取所需字段）。 */
interface ApiResponseEnvelope<T> {
  success: boolean
  data?: T
  error?: { code?: string; message?: string }
}

// ==================== 内部工具 ====================

async function parseErrorResponse(res: Response): Promise<StructuredError> {
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
  if (res.status >= 500) {
    return { code: AppErrorCode.LOGIN_SERVER_ERROR, details: { status: res.status, detail } }
  }
  return { code: AppErrorCode.LOGIN_INVALID_CREDENTIALS, details: detail ? { detail } : undefined }
}
