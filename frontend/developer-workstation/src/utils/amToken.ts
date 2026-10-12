/**
 * 浏览器侧 DSP AMToken 读取与静默续期。
 *
 * AI Generate 的模型调用直连集团 AI gateway，Bearer 凭证就是这个 token——后端不持有共享密钥，
 * 每轮对话都由前端把当前用户的 AMToken 经 `X-AM-Token` 头透传下去，gateway 侧因此审计到人。
 *
 * 取值顺序：本页静默续期拿到的 token（只在内存里）→ URL 上显式注入的 `?am_token=`（mock / 排障用）
 * → `AMToken` cookie。后两者与统一登录页的 DSP 免密流程一致（见 `frontend/login/src/api/dsp.ts`）。
 *
 * 取不到时返回空串：后端会以 `AI_GATEWAY_TOKEN_MISSING` 显式失败，不会退化成匿名调用。
 */

/** cookie 名，与后端 `ai-generation.gateway.am-token-name` / admin-center `sso.dsp.am-token-name` 对齐。 */
const AM_TOKEN_COOKIE = 'AMToken'

/**
 * 后端表示"这个用户的 AMToken 不能用了"的两个错误码：gateway 拒绝（多为 DSP 侧已过期）与根本没带。
 * 两者都只能靠换一个新 token 解决，重试同一个没有意义。
 */
export const AM_TOKEN_REAUTH_CODES: readonly string[] = ['AI_GATEWAY_UNAUTHORIZED', 'AI_GATEWAY_TOKEN_MISSING']

export function isAmTokenReauthCode(code: string | null | undefined): boolean {
  return !!code && AM_TOKEN_REAUTH_CODES.includes(code)
}

/** 静默续期拿到的 token。只在内存里：刷新页面即丢，回落 cookie，失效了会再续一次。 */
let refreshedAmToken = ''

/** 从 URL `?am_token=` 读取显式注入的 AMToken。 */
function readQueryAmToken(): string {
  try {
    return new URLSearchParams(window.location.search).get('am_token') ?? ''
  } catch {
    return ''
  }
}

/** 从 `AMToken` cookie 读取。HttpOnly cookie 读不到，此时返回空串。 */
function readCookieAmToken(): string {
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${AM_TOKEN_COOKIE}=([^;]+)`))
  return match ? decodeURIComponent(match[1]) : ''
}

/** 取当前浏览器侧 AMToken；取不到返回空串。 */
export function readAmToken(): string {
  return refreshedAmToken || readQueryAmToken() || readCookieAmToken()
}

/** 透传 AMToken 的请求头；读不到就不带（后端以 AI_GATEWAY_TOKEN_MISSING 显式失败，不做匿名调用）。 */
export function amTokenHeaders(): Record<string, string> {
  const amToken = readAmToken()
  return amToken ? { 'X-AM-Token': amToken } : {}
}

/**
 * DSP authenticate 的运行时配置：容器启动时由 docker-entrypoint.sh 写进 `runtime-config.js`，
 * 键名与统一登录页同一套（K8s 里引用同一组 ConfigMap / Secret 键）。
 * 没配 URL 即不续期——不在代码里写死任何环境的地址。
 */
interface DspRuntimeConfig {
  VITE_DSP_AUTHENTICATE_URL?: string
  VITE_DSP_CLIENT_ID?: string
  VITE_DSP_CLIENT_SECRET?: string
  VITE_DSP_ACCEPT_API_VERSION?: string
}

declare global {
  interface Window {
    __DW_RUNTIME_CONFIG__?: DspRuntimeConfig
  }
}

function dspConfig(key: keyof DspRuntimeConfig): string {
  return (window.__DW_RUNTIME_CONFIG__?.[key] ?? '').trim()
}

/** DSP authenticate 响应体（字段名各环境不一，与登录页同样做多重兼容）。 */
interface DspAuthenticateBody {
  tokenId?: string
  amToken?: string
  access_token?: string
}

/** 进行中的续期：同一时刻多处请求同时失败，只打一次 DSP。 */
let inflightRefresh: Promise<boolean> | null = null

/**
 * 向 DSP 静默换一个新 AMToken（与统一登录页同一个 authenticate 端点：Windows 集成认证，无需用户操作）。
 *
 * 返回 true 表示现在有一个**不同于被拒那个**的 token 可用——拿回同一个再重试只会得到同样的拒绝，
 * 调用方应直接走"重新登录"。未配置端点、网络/CORS 失败、非 2xx、响应里没有 token 一律返回 false
 * （失败原因进 console）。
 *
 * @param rejectedToken 请求实际带上去、被拒的那个 token（知道时传）。别的请求已经换过新 token、
 *        只是这个请求发出得早，就不必再打 DSP，直接用内存里的新 token 重试。
 */
export function refreshAmToken(rejectedToken?: string): Promise<boolean> {
  const current = readAmToken()
  if (rejectedToken !== undefined && current && current !== rejectedToken) return Promise.resolve(true)
  inflightRefresh ??= doRefreshAmToken().finally(() => {
    inflightRefresh = null
  })
  return inflightRefresh
}

async function doRefreshAmToken(): Promise<boolean> {
  const url = dspConfig('VITE_DSP_AUTHENTICATE_URL')
  if (!url) return false

  const headers: Record<string, string> = {
    'X-Requested-With': 'XMLHttpRequest',
    'Accept-API-Version': dspConfig('VITE_DSP_ACCEPT_API_VERSION') || 'protocol=1.0,resource=1.0',
  }
  const clientId = dspConfig('VITE_DSP_CLIENT_ID')
  if (clientId) headers['X-Client-Id'] = clientId
  const clientSecret = dspConfig('VITE_DSP_CLIENT_SECRET')
  if (clientSecret) headers['X-Client-Secret'] = clientSecret

  const previous = readAmToken()
  let res: Response
  try {
    // credentials:include —— 带上/接收 DSP 域的 cookie（AMToken 可能经 set-cookie 下发）
    res = await fetch(url, { method: 'POST', credentials: 'include', headers })
  } catch (e) {
    console.warn('[am-token] DSP authenticate unreachable (network / CORS); falling back to sign-in', e)
    return false
  }
  if (!res.ok) {
    console.warn(`[am-token] DSP authenticate answered HTTP ${res.status}; falling back to sign-in`)
    return false
  }
  let token = ''
  try {
    const body = (await res.json()) as DspAuthenticateBody
    token = body.tokenId || body.amToken || body.access_token || ''
  } catch {
    /* 非 JSON 响应：可能仅通过 set-cookie 下发 AMToken */
  }
  token ||= readCookieAmToken()
  if (!token || token === previous) {
    console.warn('[am-token] DSP authenticate returned no new token; falling back to sign-in')
    return false
  }
  refreshedAmToken = token
  return true
}

/** 仅测试用：清掉内存里的续期结果。 */
export function resetAmTokenForTest(): void {
  refreshedAmToken = ''
  inflightRefresh = null
}
