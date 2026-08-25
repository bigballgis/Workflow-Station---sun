/** Same-origin Guidelines portal (`/help/…`). Not tied to DW/Admin/Portal Vite base. */

export const HELP_PORTAL_PREFIX = '/help'

/** Path inside the help app, e.g. `/computed-fields` or `/email-send#connection`. */
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

export const COMPUTED_FIELD_GUIDE_ROUTE_NAME = 'ComputedFieldGuide'

export const COMPUTED_FIELD_GUIDE_PATH = '/help/computed-fields'

/** Absolute URL opened from the Formula dialog (new tab). */
export function computedFieldGuideAbsoluteUrl(): string {
  return helpGuideAbsoluteUrl('/computed-fields')
}
