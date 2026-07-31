/**
 * Ranges preset helpers for sensitive masking (half-open intervals + designer UI rows).
 * Consumed by sensitiveMask.ts; keep algorithm in sync with user-portal mirror.
 */

export interface SensitiveMaskRange {
  start: number
  end?: number | null
}

export type MaskRangeSide = 'left' | 'right'

export interface SensitiveMaskRangeUi {
  side: MaskRangeSide
  /** 0-based offset from the chosen side. */
  offset: number
  /** Number of characters to mask (>= 0). */
  length: number
}

export function normalizeMaskRanges(raw: unknown): SensitiveMaskRange[] {
  if (!Array.isArray(raw)) return []
  const out: SensitiveMaskRange[] = []
  for (const item of raw) {
    if (item == null || typeof item !== 'object') continue
    const o = item as Record<string, unknown>
    if (typeof o.start !== 'number' || !Number.isFinite(o.start)) continue
    const start = Math.trunc(o.start)
    let end: number | null | undefined
    if (o.end === null || o.end === undefined) {
      end = null
    } else if (typeof o.end === 'number' && Number.isFinite(o.end)) {
      end = Math.trunc(o.end)
    } else {
      continue
    }
    out.push(end === null ? { start, end: null } : { start, end })
  }
  return out
}

/** Resolve a possibly-negative index into [0, len]. */
export function resolveMaskIndex(index: number, len: number): number {
  if (!Number.isFinite(index)) return 0
  let i = Math.trunc(index)
  if (i < 0) i = len + i
  if (i < 0) return 0
  if (i > len) return len
  return i
}

/**
 * Convert a designer UI row into a canonical half-open range.
 * Right-side offset 0 + length N → mask the last N characters ({ start: -N }).
 */
export function uiRowToMaskRange(row: SensitiveMaskRangeUi): SensitiveMaskRange {
  const offset = Math.max(0, Math.floor(row.offset || 0))
  const length = Math.max(0, Math.floor(row.length || 0))
  if (row.side === 'left') {
    return { start: offset, end: offset + length }
  }
  if (offset === 0) {
    return { start: -length, end: null }
  }
  return { start: -(offset + length), end: -offset }
}

/** Best-effort reverse mapping for the designer list. */
export function maskRangeToUiRow(range: SensitiveMaskRange): SensitiveMaskRangeUi {
  const start = Math.trunc(range.start)
  const end = range.end
  if (start >= 0 && typeof end === 'number' && end >= start) {
    return { side: 'left', offset: start, length: end - start }
  }
  if (start < 0 && (end === null || end === undefined)) {
    return { side: 'right', offset: 0, length: Math.abs(start) }
  }
  if (start < 0 && typeof end === 'number' && end <= 0) {
    const absStart = Math.abs(start)
    const absEnd = Math.abs(end)
    if (absStart >= absEnd) {
      return { side: 'right', offset: absEnd, length: absStart - absEnd }
    }
  }
  const e = typeof end === 'number' ? end : start
  return {
    side: 'left',
    offset: Math.max(0, start),
    length: Math.max(0, e - start),
  }
}

export function applyMaskRanges(
  raw: string,
  ranges: SensitiveMaskRange[],
  maskChar: string,
): string {
  const len = raw.length
  if (len === 0) return raw
  if (!ranges.length) return raw

  const masked = new Array<boolean>(len).fill(false)
  for (const range of ranges) {
    const s = resolveMaskIndex(range.start, len)
    const e =
      range.end === null || range.end === undefined
        ? len
        : resolveMaskIndex(range.end, len)
    const from = Math.min(s, e)
    const to = Math.max(s, e)
    for (let i = from; i < to; i++) {
      masked[i] = true
    }
  }

  let out = ''
  for (let i = 0; i < len; i++) {
    out += masked[i] ? maskChar : raw.charAt(i)
  }
  return out
}
