/**
 * 解包门户 {@code ApiResponse<T>}（axios 拦截器已返回 body 对象）：优先返回 {@code data}，否则返回原对象。
 * 用于子表分配等接口，避免把外层 {@code success} 当成业务 {@code success} 或未解包导致误判失败。
 */
export function unwrapPortalApiPayload<T extends Record<string, unknown>>(
  res: unknown
): T | null {
  if (res == null || typeof res !== 'object') {
    return null
  }
  const r = res as Record<string, unknown>
  if ('data' in r && r.data !== undefined && r.data !== null && typeof r.data === 'object') {
    const inner = r.data as Record<string, unknown>
    // 极少数情况下双重包装
    if (
      'data' in inner &&
      inner.data !== undefined &&
      inner.data !== null &&
      typeof inner.data === 'object' &&
      !Array.isArray(inner.data) &&
      ('success' in (inner.data as object) || 'assigneeId' in (inner.data as object))
    ) {
      return inner.data as T
    }
    return r.data as T
  }
  return r as T
}

/**
 * 从 HTTP 错误响应体提取用户可读文案。
 * 兼容：ApiResponse.error、RFC 7807、校验 details、纯字符串 body。
 */
export function pickHttpErrorBodyMessage(data: unknown): string | undefined {
  if (data == null || data === '') return undefined
  if (typeof data === 'string') {
    const s = data.trim()
    return s.length > 0 ? s : undefined
  }
  if (typeof data !== 'object') return undefined
  const o = data as Record<string, unknown>

  const err = o.error
  if (typeof err === 'string' && err.trim().length > 0) return err.trim()
  if (err && typeof err === 'object') {
    const e = err as Record<string, unknown>
    if (typeof e.message === 'string' && e.message.trim().length > 0) return e.message.trim()
  }

  if (typeof o.message === 'string' && o.message.trim().length > 0) return o.message.trim()

  if (typeof o.detail === 'string' && o.detail.trim().length > 0) return o.detail.trim()
  if (typeof o.title === 'string' && o.title.trim().length > 0) return o.title.trim()

  const det = o.details
  if (det != null) {
    if (typeof det === 'string' && det.trim().length > 0) return det.trim()
    if (typeof det === 'object' && det !== null && !Array.isArray(det)) {
      const vals = Object.values(det as Record<string, unknown>).flatMap(v =>
        Array.isArray(v) ? v : [v]
      )
      const parts = vals
        .map(v => (typeof v === 'string' ? v : v != null ? String(v) : ''))
        .map(s => s.trim())
        .filter(Boolean)
      if (parts.length) return parts.join('; ')
    }
  }
  return undefined
}

const AXIOS_STATUS_ONLY = /^Request failed with status code \d+$/i

export function resolveUserFacingHttpMessage(error: unknown, t: (key: string) => string): string {
  const ax = error as {
    response?: { status?: number; data?: unknown }
    message?: string
    request?: unknown
  }
  const status = ax.response?.status
  const fromBody =
    ax.response?.data !== undefined ? pickHttpErrorBodyMessage(ax.response.data) : undefined
  if (fromBody) return fromBody

  if (status === 401) return t('api.unauthorized')
  if (status === 403) return t('api.noPermission')
  if (status === 400) return t('api.invalidParams')
  if (status === 404) return t('api.notFound')
  if (status === 422) return t('api.businessError')
  if (status === 429) return t('api.tooManyRequests')
  if (status === 500) return t('api.serverError')
  if (status === 502 || status === 503) return t('api.serviceUnavailable')

  const raw = ax.message?.trim()
  if (raw && !AXIOS_STATUS_ONLY.test(raw)) return raw
  if (status) return t('api.requestFailed')
  if (ax.response === undefined && ax.request) return t('api.networkError')
  return t('api.requestFailed')
}
