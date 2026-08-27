import type { ListColumnKind } from './columnMeta'
import { clampColumnWidth } from './columnResizeCursor'

/** Matches `.list-col-caret` (el-icon box is ~1em plus padding, not the 12px font-size). */
export const HEADER_CARET_PX = 16
export const HEADER_TRIGGER_GAP_PX = 4
export const HEADER_HANDLE_GUTTER_PX = 12

/**
 * Horizontal padding on `.list-data-grid .cell` (and MTV). Keep the SCSS
 * `padding-left/right` in listDataGrid.scss equal to this number.
 */
export const CELL_PADDING_X_PX = 8

/**
 * Slack for tooltip/dropdown wrappers, subpixel rounding, and Inter vs the
 * fallback face. Must keep `.list-col-label` from ellipsizing English titles.
 */
export const HEADER_FIT_PAD_PX = 24

export const HEADER_CHROME_PX =
  HEADER_CARET_PX
  + HEADER_TRIGGER_GAP_PX
  + HEADER_HANDLE_GUTTER_PX
  + CELL_PADDING_X_PX * 2
  + HEADER_FIT_PAD_PX

/** Short titles (Status / 状态) still need room for the caret and handle. */
export const HEADER_FIT_MIN = 112

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

const HEADER_FONT_FAMILY = "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"

const measureCache = new Map<string, number>()

/** Drop cached glyph widths after webfonts load so Arial-first measures cannot stick. */
export function invalidateHeaderLabelMeasureCache(): void {
  measureCache.clear()
}

function trackingPx(text: string): number {
  return HEADER_LABEL_LETTER_SPACING_EM * HEADER_LABEL_FONT_SIZE_PX * Math.max(0, text.length - 1)
}

function fallbackMeasure(text: string): number {
  return Math.ceil(text.length * 8 + trackingPx(text))
}

function measureByDom(label: string): number {
  if (typeof document === 'undefined' || !document.body) return 0
  const probe = document.createElement('span')
  probe.textContent = label
  probe.setAttribute('aria-hidden', 'true')
  probe.style.cssText = [
    'position:absolute',
    'left:-99999px',
    'top:0',
    'visibility:hidden',
    'pointer-events:none',
    'white-space:nowrap',
    'padding:0',
    'margin:0',
    'border:0',
    'line-height:1',
    `font:${HEADER_FONT}`,
    `font-family:${HEADER_FONT_FAMILY}`,
    `font-size:${HEADER_LABEL_FONT_SIZE_PX}px`,
    `font-weight:${HEADER_LABEL_FONT_WEIGHT}`,
    `letter-spacing:${HEADER_LABEL_LETTER_SPACING_EM}em`,
    'text-transform:uppercase',
  ].join(';')
  document.body.appendChild(probe)
  const width = probe.getBoundingClientRect().width
  probe.remove()
  // jsdom sometimes reports a huge box for an un-laid-out span; 11px titles stay well under this.
  if (!Number.isFinite(width) || width <= 0 || width > 400) return 0
  return width
}

function measureByCanvas(text: string): number {
  if (typeof document === 'undefined') return 0
  try {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (!ctx) return 0
    ctx.font = HEADER_FONT
    const width = ctx.measureText(text).width
    if (!Number.isFinite(width) || width <= 0) return 0
    return width + trackingPx(text)
  } catch {
    return 0
  }
}

/**
 * Measure the current-locale header label with the same face the table paints:
 * 11px, weight 600, uppercase, 0.08em letter-spacing.
 * Prefer an offscreen span so CSS letter-spacing is not guessed on top of canvas.
 * FALLBACK(ux): jsdom's layout/canvas often returns 0 — approximate so unit tests can still
 * assert clamp/min without a real glyph rasterizer. Live browsers measure.
 */
export function measureHeaderLabelPx(label: string): number {
  const key = label ?? ''
  const cached = measureCache.get(key)
  if (cached != null) return cached
  const text = key.toLocaleUpperCase()
  const width = measureByDom(key) || measureByCanvas(text) || fallbackMeasure(text)
  measureCache.set(key, width)
  return width
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
