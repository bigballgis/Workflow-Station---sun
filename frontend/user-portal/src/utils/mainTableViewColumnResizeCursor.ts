/** Dark resize cursor — avoids system ew-resize appearing white on light headers (Windows). */
const MTV_COL_RESIZE_CURSOR_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20">' +
  '<line x1="8" y1="3" x2="8" y2="17" stroke="#303133" stroke-width="2"/>' +
  '<line x1="12" y1="3" x2="12" y2="17" stroke="#303133" stroke-width="2"/>' +
  '<path d="M4 10 L6.5 7 L6.5 13 Z" fill="#303133"/>' +
  '<path d="M16 10 L13.5 7 L13.5 13 Z" fill="#303133"/>' +
  '</svg>'

export const MTV_COL_RESIZE_CURSOR = `url("data:image/svg+xml,${encodeURIComponent(
  MTV_COL_RESIZE_CURSOR_SVG,
)}") 10 10, ew-resize`

export const COLUMN_WIDTH_MIN = 60
export const COLUMN_WIDTH_MAX = 600

export function clampColumnWidth(width: number): number {
  return Math.min(COLUMN_WIDTH_MAX, Math.max(COLUMN_WIDTH_MIN, width))
}
