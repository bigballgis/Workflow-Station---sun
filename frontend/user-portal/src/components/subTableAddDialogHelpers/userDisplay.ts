import type { UserSnapshotViewField } from './types'

/** Sub-table cell may store a user id string or a user object ({ id, name, ... }). */
export function extractUserIdFromCellValue(raw: unknown): string {
  if (raw == null) return ''
  if (typeof raw === 'string' || typeof raw === 'number') return String(raw).trim()
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    const o = raw as Record<string, unknown>
    const idPart = o.id ?? o.userId
    if (idPart != null && typeof idPart !== 'object') return String(idPart).trim()
  }
  return ''
}

/**
 * Portal / relation APIs often return user rows as snake_case objects (id, full_name, display_name, …).
 * Prefer full_name / name for plain-text cells; use {@link userObjectTagDisplayString} for lookup-style tag (usually id).
 */
export function unwrapUserLikeValueToDisplayString(rawValue: unknown): string {
  if (rawValue === null || rawValue === undefined) return '-'
  if (typeof rawValue !== 'object' || Array.isArray(rawValue)) {
    return String(rawValue)
  }
  const o = rawValue as Record<string, unknown>
  const preferKeys = [
    'full_name',
    'fullName',
    'displayName',
    'display_name',
    'name',
    'username',
    'email',
    'label',
    'title'
  ] as const
  for (const k of preferKeys) {
    const v = o[k]
    if (v != null && typeof v !== 'object') {
      const s = String(v).trim()
      if (s && s !== '-') return s
    }
  }
  const idVal = o.id ?? o.userId
  if (idVal != null && typeof idVal !== 'object') {
    const s = String(idVal).trim()
    if (s) return s
  }
  return '-'
}

/** Lookup pill: show primary id (matches assignee / user snapshot UX in designer preview). */
export function userObjectTagDisplayString(rawValue: unknown): string {
  if (rawValue === null || rawValue === undefined) return '-'
  if (typeof rawValue !== 'object' || Array.isArray(rawValue)) {
    return unwrapUserLikeValueToDisplayString(rawValue)
  }
  const o = rawValue as Record<string, unknown>
  const idVal = o.id ?? o.userId
  if (idVal != null && typeof idVal !== 'object') {
    const s = String(idVal).trim()
    if (s) return s
  }
  return unwrapUserLikeValueToDisplayString(rawValue)
}

export function isUserSnapshotLikeObject(raw: unknown): boolean {
  if (raw == null || typeof raw !== 'object' || Array.isArray(raw)) return false
  const o = raw as Record<string, unknown>
  const keys = Object.keys(o)
  if (keys.length < 2) return false
  const hasId = o.id != null && typeof o.id !== 'object'
  const hasHints =
    o.username != null ||
    o.full_name != null ||
    o.fullName != null ||
    o.email != null ||
    o.display_name != null ||
    o.displayName != null
  return !!(hasId && hasHints)
}

/** Ordered fields for el-descriptions (keys must exist on row object for cell binding). */
export function userSnapshotViewFieldsFromRow(raw: unknown): UserSnapshotViewField[] {
  if (!isUserSnapshotLikeObject(raw)) return []
  const o = raw as Record<string, unknown>
  const preferredOrder = [
    'id',
    'username',
    'display_name',
    'displayName',
    'full_name',
    'fullName',
    'email',
    'employee_id',
    'employeeId',
    'status',
    'language'
  ]
  const out: UserSnapshotViewField[] = []
  const seen = new Set<string>()
  for (const k of preferredOrder) {
    if (seen.has(k) || !(k in o) || o[k] === undefined) continue
    out.push({ key: k, label: k })
    seen.add(k)
  }
  return out
}

export function formatUserSnapshotCellValue(val: unknown): string {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'object') return '-'
  const s = String(val).trim()
  return s || '-'
}
