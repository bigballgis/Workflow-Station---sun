/** Shared presentation helpers for the Automation page panels. */

/** ISO timestamp → local "YYYY-MM-DD HH:mm"; blank-safe. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

/**
 * Business-key generator for new flows: display-name slug + 6 random chars,
 * unique enough per environment while staying readable in exports and BPMN.
 */
export function generateFlowKey(displayName: string): string {
  const slug = displayName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40)
  const random = Math.random().toString(36).slice(2, 8)
  return slug ? `${slug}-${random}` : random
}
