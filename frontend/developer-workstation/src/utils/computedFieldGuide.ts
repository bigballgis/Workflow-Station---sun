/** Shared route id and in-app URL for the computed-field formula guide. */

export const COMPUTED_FIELD_GUIDE_ROUTE_NAME = 'ComputedFieldGuide'

export const COMPUTED_FIELD_GUIDE_PATH = 'help/computed-fields'

/** Absolute URL shown in the Formula dialog tooltip and opened in a new tab. */
export function computedFieldGuideAbsoluteUrl(): string {
  const origin = typeof window === 'undefined' ? '' : window.location.origin
  const base = String(import.meta.env.BASE_URL || '/').replace(/\/?$/, '/')
  return `${origin}${base}${COMPUTED_FIELD_GUIDE_PATH}`
}
