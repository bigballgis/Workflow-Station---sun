/**
 * 业务错误码枚举
 *
 * 按 RULES.md Rule 6 要求：所有错误必须标准化为 { code, details } 结构。
 * API 层返回 AppErrorCode，UI 层通过 errorTranslator 映射为 i18n key。
 */
export enum AppErrorCode {
  /** URL 缺少 client_id 或 redirect_uri 参数 */
  LOGIN_MISSING_PARAMS = 'LOGIN_MISSING_PARAMS',

  /** 网络请求失败（fetch throw） */
  LOGIN_NETWORK_ERROR = 'LOGIN_NETWORK_ERROR',

  /** 后端响应缺少 authorizationCode 或 redirectUri */
  LOGIN_INVALID_RESPONSE = 'LOGIN_INVALID_RESPONSE',

  /** 后端 5xx 错误 */
  LOGIN_SERVER_ERROR = 'LOGIN_SERVER_ERROR',

  /** 后端 4xx 错误（凭证无效或 redirect_uri 被拒绝） */
  LOGIN_INVALID_CREDENTIALS = 'LOGIN_INVALID_CREDENTIALS',

  /** DSP 免密不可用（后端未启用 / 503） */
  DSP_DISABLED = 'DSP_DISABLED',

  /** 浏览器侧未取得 DSP AMToken */
  DSP_NO_TOKEN = 'DSP_NO_TOKEN',

  /** 浏览器侧主动调用 DSP authenticate 获取 AMToken 失败（网络/CORS/4xx/5xx） */
  DSP_AUTH_FAILED = 'DSP_AUTH_FAILED',

  /** DSP 免密失败（4xx / 解析失败） */
  DSP_FAILED = 'DSP_FAILED',
}

/** 标准化错误结构（RULES.md Rule 6） */
export interface StructuredError {
  /** 机器可消费的错误码 */
  code: AppErrorCode

  /** 附加上下文（如 HTTP status、后端 detail message 等） */
  details?: Record<string, unknown>
}
