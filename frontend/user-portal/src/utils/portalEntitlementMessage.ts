/**
 * Prefer server-translated entitlement text; if MessageSource leaked an i18n key, use locale fallback.
 */
export function resolvePortalEntitlementMessage(
  serverMessage: string | null | undefined,
  fallback: string
): string {
  const msg = typeof serverMessage === 'string' ? serverMessage.trim() : ''
  if (
    !msg ||
    msg === 'auth.portal_entitlement_denied' ||
    /^[a-z]+(?:\.[a-z0-9_]+)+$/i.test(msg)
  ) {
    return fallback
  }
  return msg
}
