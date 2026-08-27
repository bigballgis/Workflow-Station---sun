import type { ListColumnKind } from './columnMeta'
import { clampColumnWidth, COLUMN_WIDTH_MAX, COLUMN_WIDTH_MIN } from './columnResizeCursor'

/** Matches `.list-col-caret` / `.list-col-trigger` gap / `.list-col-header` padding-right. */
export const HEADER_CARET_PX = 12
export const HEADER_TRIGGER_GAP_PX = 4
export const HEADER_HANDLE_GUTTER_PX = 12

/**
 * Horizontal padding on `.list-data-grid .cell` (and MTV). Keep the SCSS
 * `padding-left/right` in listDataGrid.scss equal to this number.
 */
export const CELL_PADDING_X_PX = 8

/**
 * Non-label pixels inside a column: cell pad + caret + gap + resize gutter.
 * Must match the space `.list-col-label` actually has, or English headers ellipsis
 * while neighbouring TEXT columns still look empty.
 */
export const HEADER_CHROME_PX =
  HEADER_CARET_PX + HEADER_TRIGGER_GAP_PX + HEADER_HANDLE_GUTTER_PX + CELL_PADDING_X_PX * 2

/**
 * Canvas measures Arial; thead paints Inter. A few extra pixels keep
 * "Current Assignee" from ellipsizing after chrome is subtracted.
 */
export const HEADER_FIT_SLACK_PX = 24

/** Short titles (Status / 状态) still need room for the caret and handle. */
export const HEADER_FIT_MIN = 100

/**
 * Portal/Admin table headers are 11px / 600 / uppercase / 0.08em tracking
 * (`ws-theme.scss` thead `.cell`). Measuring 14px mixed-case under-fits
 * "Process Title" / "Current Assignee".
 */
export const HEADER_LABEL_FONT_SIZE_PX = 11
export const HEADER_LABEL_FONT_WEIGHT = 600
export const HEADER_LABEL_LETTER_SPACING_EM = 0.08

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

const HEADER_FONT =
  `${HEADER_LABEL_FONT_WEIGHT} ${HEADER_LABEL_FONT_SIZE_PX}px Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif`

/**
 * Measure the current-locale header label with the same face the table paints:
 * 11px, weight 600, uppercase, 0.08em letter-spacing.
 * FALLBACK(ux): jsdom's canvas often returns 0 — approximate so unit tests can still
 * assert clamp/min without a real glyph rasterizer. Live browsers measure.
 */
export function measureHeaderLabelPx(label: string): number {
  const text = (label ?? '').toLocaleUpperCase()
  const tracking =
    HEADER_LABEL_LETTER_SPACING_EM * HEADER_LABEL_FONT_SIZE_PX * Math.max(0, text.length - 1)
  if (typeof document !== 'undefined') {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (ctx) {
      ctx.font = HEADER_FONT
      const width = ctx.measureText(text).width
      if (Number.isFinite(width) && width > 0) {
        return width + tracking
      }
    }
  }
  return Math.ceil(text.length * 8 + tracking)
}

/** Default base width: max(header text + chrome, kind content floor), clamped. */
export function headerFitColumnWidth(label: string, kind?: ListColumnKind): number {
  const header = Math.max(
    HEADER_FIT_MIN,
    Math.round(measureHeaderLabelPx(label) + HEADER_CHROME_PX + HEADER_FIT_SLACK_PX),
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
