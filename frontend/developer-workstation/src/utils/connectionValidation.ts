/** Aligns with backend EmailConnectionRequest.name @Email (local part + @ + domain). */
export const CONNECTION_SENDER_EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidSenderEmail(value: string): boolean {
  return CONNECTION_SENDER_EMAIL_PATTERN.test(value.trim())
}

export function hasConnectionNameValidationError(details: unknown): boolean {
  if (details == null || typeof details !== 'object') return false
  return Object.prototype.hasOwnProperty.call(details, 'name')
}

export function shouldMapRawMessageToInvalidSenderEmail(raw: string | undefined): boolean {
  if (!raw) return false
  return /(\(name=|['"]name['"]\s*:|name=).*(email|Email|address)/i.test(raw)
}

export function resolveConnectionSaveErrorMessage(
  error: unknown,
  t: (key: string) => string,
  resolveRawMessage: (error: unknown) => string | undefined,
): string {
  const ax = error as {
    response?: { data?: { error?: { details?: Record<string, unknown> } } }
  }
  const details = ax.response?.data?.error?.details
  if (hasConnectionNameValidationError(details)) {
    return t('connection.emailAddressInvalid')
  }
  const raw = resolveRawMessage(error)
  if (shouldMapRawMessageToInvalidSenderEmail(raw)) {
    return t('connection.emailAddressInvalid')
  }
  return raw || t('common.saveFailed')
}

export function formatConnectionTestFailureMessage(
  data: { detail?: unknown } | undefined,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  const detail = typeof data?.detail === 'string' ? data.detail.trim() : ''
  return detail ? t('connection.testFailedDetail', { detail }) : t('connection.testFailed')
}

export function isMessageBoxCancel(error: unknown): boolean {
  if (error === 'cancel') return true
  if (typeof error !== 'object' || error == null) return false
  return (error as { action?: string }).action === 'cancel'
}
