/**
 * 改密接口失败时的文案：400 可能是「当前密码错误」（空 body）或校验错误（有 message/errors）；
 * 401 表示令牌无效等，不应提示为「当前密码错误」。
 */

export function extractHttpErrorMessage(data: unknown): string | undefined {
  if (data == null || typeof data !== 'object') return undefined
  const o = data as Record<string, unknown>

  const err = o.error as Record<string, unknown> | undefined
  if (err && typeof err.message === 'string' && err.message.trim()) {
    return err.message.trim()
  }
  if (typeof o.message === 'string' && o.message.trim()) {
    return o.message.trim()
  }
  if (typeof o.detail === 'string' && o.detail.trim()) {
    return o.detail.trim()
  }

  if (Array.isArray(o.errors)) {
    const parts: string[] = []
    for (const e of o.errors) {
      if (e && typeof e === 'object') {
        const row = e as { defaultMessage?: string; message?: string }
        const m = row.defaultMessage || row.message
        if (typeof m === 'string' && m.trim()) parts.push(m.trim())
      }
    }
    if (parts.length) return parts.join('; ')
  }

  if (o.details && typeof o.details === 'object') {
    const vals = Object.values(o.details as Record<string, unknown>)
      .map((v) => (v == null ? '' : String(v)))
      .filter((s) => s.length > 0)
    if (vals.length) return vals.join('; ')
  }

  return undefined
}

export function getChangePasswordFailureMessage(
  error: unknown,
  translate: (key: string) => string
): string {
  const err = error as { response?: { status?: number; data?: unknown } }
  const status = err.response?.status
  const data = err.response?.data

  const fromBody = extractHttpErrorMessage(data)

  if (status === 400) {
    if (fromBody) return fromBody
    return translate('profile.wrongCurrentPassword')
  }
  if (status === 401) {
    return translate('profile.changePasswordUnauthorized')
  }
  if (fromBody) return fromBody
  return translate('common.failed')
}
