import { describe, expect, it } from 'vitest'
import {
  ZOOM_STEP,
  clampZoomScale,
  scaleAfterWheel,
  scrollToKeepPoint,
} from '../filePreviewZoom'

describe('filePreviewZoom', () => {
  it('clamps scale to the allowed range', () => {
    expect(clampZoomScale(0)).toBe(0.1)
    expect(clampZoomScale(99)).toBe(8)
    expect(clampZoomScale(Number.NaN)).toBe(1)
  })

  it('zooms in when the wheel delta is negative', () => {
    expect(scaleAfterWheel(1, -100)).toBe(ZOOM_STEP)
    expect(scaleAfterWheel(1, 100)).toBe(1 / ZOOM_STEP)
  })

  it('keeps the pointer over the same content point after scaling', () => {
    expect(scrollToKeepPoint(0, 0, 100, 80, 1, 2)).toEqual({
      scrollLeft: 100,
      scrollTop: 80,
    })
    expect(scrollToKeepPoint(100, 80, 50, 40, 2, 1)).toEqual({
      scrollLeft: 25,
      scrollTop: 20,
    })
  })
})
