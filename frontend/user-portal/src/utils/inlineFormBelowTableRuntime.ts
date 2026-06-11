/**
 * Row selection for form-below-table in MI link-form isolation mode.
 * When nested child rows exist under the current participant parent, inline form must
 * still hydrate even if more than one child row shares the same FK (e.g. sub_task_id).
 */

export function resolveMiLinkIsolateInlineRow(
  rows: unknown[],
  parentId: string | number | null | undefined,
  matchRowIndex: (rows: unknown[], parentId: string | number) => number,
  pickFallbackRow: (rows: unknown[]) => unknown | null,
): Record<string, unknown> | null {
  if (!Array.isArray(rows)) return null
  if (rows.length === 1) {
    const only = rows[0]
    return only && typeof only === 'object' ? { ...(only as Record<string, unknown>) } : null
  }
  if (rows.length === 0) return {}
  if (parentId == null || String(parentId).trim() === '') return null

  const idx = matchRowIndex(rows, parentId)
  if (idx >= 0) {
    const hit = rows[idx]
    return hit && typeof hit === 'object' ? { ...(hit as Record<string, unknown>) } : null
  }

  const pick = pickFallbackRow(rows)
  return pick && typeof pick === 'object' ? { ...(pick as Record<string, unknown>) } : null
}
