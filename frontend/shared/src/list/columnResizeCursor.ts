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
/** Visual drag can exceed the persisted base max once leftover is distributed. */
export const COLUMN_DISPLAY_WIDTH_MAX = 4000

export function clampColumnWidth(width: number): number {
  return Math.min(COLUMN_WIDTH_MAX, Math.max(COLUMN_WIDTH_MIN, width))
}

export function clampDisplayWidth(width: number): number {
  return Math.min(COLUMN_DISPLAY_WIDTH_MAX, Math.max(COLUMN_WIDTH_MIN, width))
}

/**
 * Visual column width at mousedown.
 * FALLBACK(ux): stored width when the handle is not inside a th (unit tests).
 */
export function startWidthFromHandle(handle: HTMLElement, fallback: number): number {
  const cell = handle.closest('th')
  if (!cell) return clampDisplayWidth(fallback)
  const width = cell.getBoundingClientRect().width
  if (!Number.isFinite(width) || width <= 0) return clampDisplayWidth(fallback)
  return clampDisplayWidth(width)
}

export interface ColumnResizeGuide {
  move: (width: number) => void
  detach: () => void
}

function nearestScrollport(el: HTMLElement): HTMLElement | null {
  let parent = el.parentElement
  while (parent) {
    const style = getComputedStyle(parent)
    if (
      style.overflowX === 'auto' || style.overflowX === 'scroll'
      || style.overflowY === 'auto' || style.overflowY === 'scroll'
    ) {
      return parent
    }
    parent = parent.parentElement
  }
  return null
}

function intersectVertical(
  tableRect: DOMRect,
  clipRect: DOMRect,
): { top: number; height: number } | null {
  const top = Math.max(tableRect.top, clipRect.top)
  const bottom = Math.min(tableRect.bottom, clipRect.bottom)
  const height = bottom - top
  if (height <= 0) return null
  return { top, height }
}

/**
 * Full-height column guide drawn on `document.body` while a drag is in progress.
 * Position is computed from the handle's starting right edge plus the width delta,
 * so it does not wait for the table DOM to reflow. Height is clipped to the
 * table's visible intersection with its scrollport so the line does not paint
 * through pagination sitting below the grid.
 */
export function attachColumnResizeGuide(
  handle: HTMLElement,
  startWidth: number,
): ColumnResizeGuide {
  const table = handle.closest('.el-table') as HTMLElement | null
  const line = document.createElement('div')
  line.className = 'col-resize-guide'
  line.setAttribute('aria-hidden', 'true')
  document.body.appendChild(line)

  const startRight = handle.getBoundingClientRect().right

  function move(width: number) {
    line.style.left = `${startRight + (width - startWidth) - 1}px`
    const tableRect = table?.getBoundingClientRect()
    if (table && tableRect && tableRect.height > 0) {
      const scroll = nearestScrollport(table)
      const box = scroll
        ? intersectVertical(tableRect, scroll.getBoundingClientRect())
        : { top: tableRect.top, height: tableRect.height }
      if (box) {
        line.style.top = `${box.top}px`
        line.style.height = `${box.height}px`
        return
      }
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
