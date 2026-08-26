/**
 * Same-origin Guidelines portal (`/help/…`).
 * Counterpart of frontend/developer-workstation/src/utils/computedFieldGuide.ts
 * (`helpGuideAbsoluteUrl`).
 */

export const HELP_PORTAL_PREFIX = '/help'

export function helpGuideAbsoluteUrl(guidePath: string): string {
  const raw = guidePath.trim()
  const withSlash = raw.startsWith('/') ? raw : `/${raw}`
  const full = withSlash.startsWith(`${HELP_PORTAL_PREFIX}/`) || withSlash === HELP_PORTAL_PREFIX
    ? withSlash
    : `${HELP_PORTAL_PREFIX}${withSlash}`
  if (typeof window === 'undefined') {
    return full
  }
  return `${window.location.origin}${full}`
}
