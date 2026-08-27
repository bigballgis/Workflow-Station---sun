import type { ListColumnKind } from './columnMeta'
import { clampColumnWidth, COLUMN_WIDTH_MAX, COLUMN_WIDTH_MIN } from './columnResizeCursor'

/** Caret + gap + resize handle + `.list-col-header` padding-right. */
export const HEADER_CHROME_PX = 44

/** Short titles (Status / 状态) still need room for the caret and handle. */
export const HEADER_FIT_MIN = 100

/**
 * Typical cell content, not the current page's longest value.
 * Header-fit still wins when the localized title is wider.
 */
export const KIND_CONTENT_FLOOR: Record<ListColumnKind, number> = {
  TEXT: 168,
  DATETIME: 180,
  USER: 120,
  ENUM: HEADER_FIT_MIN,
  BOOLEAN: HEADER_FIT_MIN,
  NUMBER: HEADER_FIT_MIN,
}

const HEADER_FONT = "14px var(--el-font-family, Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif)"

/**
 * Measure the current-locale header label with the same 14px face the table uses.
 * FALLBACK(ux): jsdom's canvas often returns 0 — approximate so unit tests can still
 * assert clamp/min without a real glyph rasterizer. Live browsers measure.
 */
export function measureHeaderLabelPx(label: string): number {
  const text = label ?? ''
  if (typeof document !== 'undefined') {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (ctx) {
      ctx.font = HEADER_FONT
      const width = ctx.measureText(text).width
      if (Number.isFinite(width) && width > 0) {
        return width
      }
    }
  }
  return Math.ceil(text.length * 8)
}

/** Default base width: max(header text + chrome, kind content floor), clamped. */
export function headerFitColumnWidth(label: string, kind?: ListColumnKind): number {
  const header = Math.max(
    HEADER_FIT_MIN,
    Math.round(measureHeaderLabelPx(label) + HEADER_CHROME_PX),
  )
  const contentFloor = kind ? KIND_CONTENT_FLOOR[kind] : HEADER_FIT_MIN
  return clampColumnWidth(Math.max(header, contentFloor))
}

/**
 * Turn data-column base widths into on-screen widths. Leftover viewport (after
 * checkbox / Action) is shared in proportion to each base; remainder pixels go
 * to the last data column. Overflow (slack ≤ 0) keeps bases and the table scrolls.
 */
export function distributeDisplayWidths(
  bases: readonly number[],
  viewportWidth: number,
  extraWidth: number,
): number[] {
  const n = bases.length
  if (n === 0) return []
  const sum = bases.reduce((total, width) => total + width, 0)
  const slack = viewportWidth - extraWidth - sum
  if (viewportWidth <= 0 || slack <= 0) {
    return bases.slice()
  }
  const displays = bases.map((base) => base + Math.round((slack * base) / sum))
  const allocated = displays.reduce((total, width) => total + width, 0)
  displays[n - 1] += sum + slack - allocated
  return displays
}

/**
 * Invert a mouse display width back to the base that, after {@link distributeDisplayWidths},
 * yields that display. Persist this, never the leftover share.
 */
export function invertBaseWidth(
  desiredDisplay: number,
  fieldIndex: number,
  bases: readonly number[],
  viewportWidth: number,
  extraWidth: number,
): number {
  const currentBase = bases[fieldIndex] ?? COLUMN_WIDTH_MIN
  const others = bases.reduce((total, width, index) => (
    total + (index === fieldIndex ? 0 : width)
  ), 0)
  const dataViewport = viewportWidth - extraWidth
  const slack = dataViewport - (others + currentBase)
  if (viewportWidth <= 0 || dataViewport <= 0 || slack <= 0) {
    return clampColumnWidth(desiredDisplay)
  }
  // One data column already eats every leftover pixel; dragging cannot change the display.
  if (others <= 0) {
    return currentBase
  }
  let low = COLUMN_WIDTH_MIN
  let high = COLUMN_WIDTH_MAX
  let best = currentBase
  let bestDiff = Number.POSITIVE_INFINITY
  for (let step = 0; step < 32; step += 1) {
    const mid = (low + high) / 2
    const trial = bases.map((width, index) => (index === fieldIndex ? mid : width))
    const display = distributeDisplayWidths(trial, viewportWidth, extraWidth)[fieldIndex]
    const diff = Math.abs(display - desiredDisplay)
    if (diff < bestDiff) {
      best = mid
      bestDiff = diff
    }
    if (display < desiredDisplay) low = mid
    else high = mid
  }
  return clampColumnWidth(Math.round(best))
}
