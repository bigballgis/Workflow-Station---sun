/** Dark resize cursor — avoids system ew-resize appearing white on light headers (Windows). */
const COL_RESIZE_CURSOR_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20">' +
  '<line x1="8" y1="3" x2="8" y2="17" stroke="#303133" stroke-width="2"/>' +
  '<line x1="12" y1="3" x2="12" y2="17" stroke="#303133" stroke-width="2"/>' +
  '<path d="M4 10 L6.5 7 L6.5 13 Z" fill="#303133"/>' +
  '<path d="M16 10 L13.5 7 L13.5 13 Z" fill="#303133"/>' +
  '</svg>'

export const COL_RESIZE_CURSOR = `url("data:image/svg+xml,${encodeURIComponent(
  COL_RESIZE_CURSOR_SVG,
)}") 10 10, ew-resize`

export const COLUMN_WIDTH_MIN = 60
export const COLUMN_WIDTH_MAX = 600

export function clampColumnWidth(width: number): number {
  return Math.min(COLUMN_WIDTH_MAX, Math.max(COLUMN_WIDTH_MIN, width))
}

/** Empty width parked at the trailing edge so other columns keep a 1:1 drag. */
export function leftoverColumnWidth(viewportWidth: number, totalColumnWidth: number): number {
  if (viewportWidth <= 0 || totalColumnWidth <= 0) return 0
  return Math.max(0, viewportWidth - totalColumnWidth)
}

/**
 * Visual column width at mousedown.
 * FALLBACK(ux): stored width when the handle is not inside a th (unit tests).
 */
export function startWidthFromHandle(handle: HTMLElement, fallback: number): number {
  const cell = handle.closest('th')
  if (!cell) return clampColumnWidth(fallback)
  const width = cell.getBoundingClientRect().width
  if (!Number.isFinite(width) || width <= 0) return clampColumnWidth(fallback)
  return clampColumnWidth(width)
}

export interface ColumnResizeGuide {
  move: (width: number) => void
  detach: () => void
}

/**
 * Full-height column guide drawn on `document.body` while a drag is in progress.
 * Position is computed from the handle's starting right edge plus the width delta,
 * so it does not wait for the table DOM to reflow.
 */
export function attachColumnResizeGuide(
  handle: HTMLElement,
  startWidth: number,
): ColumnResizeGuide {
  const table = handle.closest('.el-table')
  const line = document.createElement('div')
  line.className = 'col-resize-guide'
  line.setAttribute('aria-hidden', 'true')
  document.body.appendChild(line)

  const startRight = handle.getBoundingClientRect().right

  function move(width: number) {
    const tableRect = table?.getBoundingClientRect()
    line.style.left = `${startRight + (width - startWidth) - 1}px`
    if (tableRect && tableRect.height > 0) {
      line.style.top = `${tableRect.top}px`
      line.style.height = `${tableRect.height}px`
      return
    }
    // FALLBACK(ux): unit tests mount the handle without el-table; live grids always have one.
    const handleRect = handle.getBoundingClientRect()
    line.style.top = `${handleRect.top}px`
    line.style.height = `${handleRect.height}px`
  }

  move(startWidth)
  return {
    move,
    detach() {
      line.remove()
    },
  }
}
