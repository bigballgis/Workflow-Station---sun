export const ZOOM_STEP = 1.25
export const ZOOM_MIN = 0.1
export const ZOOM_MAX = 8

export function clampZoomScale(scale: number): number {
  if (!Number.isFinite(scale)) return 1
  return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, scale))
}

export function scaleAfterWheel(current: number, deltaY: number): number {
  const factor = deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP
  return clampZoomScale(current * factor)
}

/** Keep the content point under the pointer after a CSS scale change. */
export function scrollToKeepPoint(
  scrollLeft: number,
  scrollTop: number,
  pointX: number,
  pointY: number,
  prevScale: number,
  nextScale: number,
): { scrollLeft: number; scrollTop: number } {
  if (!(prevScale > 0) || prevScale === nextScale) {
    return { scrollLeft, scrollTop }
  }
  const ratio = nextScale / prevScale
  return {
    scrollLeft: (scrollLeft + pointX) * ratio - pointX,
    scrollTop: (scrollTop + pointY) * ratio - pointY,
  }
}
