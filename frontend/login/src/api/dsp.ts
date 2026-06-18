/**
 * DSP 浏览器侧认证模块
 *
 * 对齐源项目壳应用：在浏览器侧主动 POST DSP authenticate 端点（dspAuthenticate.jsp），
 * 携带 X-Client-Id / X-Client-Secret / Accept-API-Version / X-Requested-With，且 credentials:include，
 * 换取 AMToken（tokenId）。随后由 useDspLogin 用该 AMToken 调后端 /sso/passwordless 换 SSO code。
 *
 * 设计要点：
 *  - 优先复用已存在的 AMToken（URL ?am_token= 或 cookie AMToken），便于 mock/网关已注入的场景；
 *  - 仅当配置了 VITE_DSP_AUTHENTICATE_URL 时才主动发起获取，避免无配置时的无谓跨域请求；
 *  - 纯 API 层：不依赖 vue-i18n，返回 AppErrorCode 交 UI 层翻译。
 *
 * 安全提示：X-Client-Secret 会在浏览器侧暴露，属源项目 public client 设计。生产应尽量改由网关注入，
 *           或确认该 secret 为可公开的 public client 凭证（见 .kiro #1458 / security-guard）。
 */

import { AppErrorCode } from '@/types/errors'
const DEFAULT_DSP_AUTHENTICATE_URL =
  'https://cmb-staff-dsp-uat.hk.hsbc:8443/dsp/dspAuthenticate.jsp?realm=staff&service=wdssoservice'
const DEFAULT_DSP_CLIENT_ID = 'HERMES'
const DEFAULT_DSP_ACCEPT_API_VERSION = 'protocol=1.0,resource=2.1'
const DSP_AUTHENTICATE_URL =
  (import.meta.env.VITE_DSP_AUTHENTICATE_URL as string | undefined)?.trim() ||
  DEFAULT_DSP_AUTHENTICATE_URL
const DSP_CLIENT_ID =
  (import.meta.env.VITE_DSP_CLIENT_ID as string | undefined)?.trim() || DEFAULT_DSP_CLIENT_ID
const DSP_CLIENT_SECRET = (import.meta.env.VITE_DSP_CLIENT_SECRET as string | undefined)?.trim() || ''
const DSP_ACCEPT_API_VERSION =
  (import.meta.env.VITE_DSP_ACCEPT_API_VERSION as string | undefined)?.trim() ||
  DEFAULT_DSP_ACCEPT_API_VERSION
/** 获取 AMToken 的结果（联合语义，避免 throw）。 */
export interface AmTokenResult {
  ok: boolean
  /** 成功时的 AMToken（tokenId）。 */
  token?: string
  /** 失败时的错误码。 */
  code?: AppErrorCode
  /** 失败时的附加信息（HTTP status / 'network' 等），用于 i18n detail。 */
  detail?: string
}
/** 是否已配置浏览器侧 DSP authenticate（决定是否主动获取 AMToken）。 */
export function isDspAuthenticateConfigured(): boolean {
  return !!DSP_AUTHENTICATE_URL
}
/** 从 URL ?am_token= 或 cookie AMToken 读取「已存在」的浏览器侧 AMToken。 */
export function readExistingAmToken(): string {
  const fromQuery = new URLSearchParams(window.location.search).get('am_token')
  if (fromQuery) return fromQuery
  const match = document.cookie.match(/(?:^|;\s*)AMToken=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}
/** DSP authenticate 响应体（仅取所需字段；不同环境字段名可能不同，做多重兼容）。 */
interface DspAuthenticateBody {
  tokenId?: string
  amToken?: string
  access_token?: string
}
/**
 * 浏览器侧主动获取 AMToken。
 *
 * @returns 成功 {@link AmTokenResult.ok}=true 且带 token；失败带 code/detail。
 */
export async function acquireAmToken(): Promise<AmTokenResult> {
  const url = DSP_AUTHENTICATE_URL
  if (!url) {
    // 未配置 authenticate 端点：无法主动获取
    return { ok: false, code: AppErrorCode.DSP_NO_TOKEN }
  }
  // 组装请求头：Accept-API-Version + X-Requested-With 必带；client id/secret 仅在配置时附加。
  const headers: Record<string, string> = {
    'X-Requested-With': 'XMLHttpRequest',
    'Accept-API-Version': DSP_ACCEPT_API_VERSION,
  }
  const clientId = DSP_CLIENT_ID
  if (clientId) headers['X-Client-Id'] = clientId
  const clientSecret = DSP_CLIENT_SECRET
  if (clientSecret) headers['X-Client-Secret'] = clientSecret
  let res: Response
  try {
    // credentials:include —— 让浏览器带上/接收 DSP 域的 cookie（AMToken 可能经 set-cookie 下发）
    res = await fetch(url, { method: 'POST', credentials: 'include', headers })
  } catch {
    // 跨域被拒 / 网络不可达：典型为 DSP 未放行本前端 origin 的 CORS（需网关/DSP 侧配置）
    return { ok: false, code: AppErrorCode.DSP_AUTH_FAILED, detail: 'network' }
  }
  if (!res.ok) {
    return { ok: false, code: AppErrorCode.DSP_AUTH_FAILED, detail: String(res.status) }
  }
  // 优先 JSON 里的 tokenId（=AMToken）；兼容 amToken/access_token；再退回 cookie。
  let token = ''
  try {
    const body = (await res.json()) as DspAuthenticateBody
    token = body.tokenId || body.amToken || body.access_token || ''
  } catch {
    /* 非 JSON 响应：可能仅通过 set-cookie 下发 AMToken */
  }
  if (!token) token = readExistingAmToken()
  if (!token) {
    return { ok: false, code: AppErrorCode.DSP_NO_TOKEN }
  }
  return { ok: true, token }
}