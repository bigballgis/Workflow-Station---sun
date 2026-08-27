import { afterEach, describe, expect, it } from 'vitest'
import { COLUMN_WIDTH_MAX, COLUMN_WIDTH_MIN } from '../columnResizeCursor'
import {
  HEADER_FIT_MIN,
  KIND_CONTENT_FLOOR,
  distributeDisplayWidths,
  headerFitColumnWidth,
  invertBaseWidth,
} from '../columnWidthLayout'

describe('distributeDisplayWidths', () => {
  it('returns bases unchanged when the viewport is not wider than the columns', () => {
    expect(distributeDisplayWidths([120, 180], 300, 48)).toEqual([120, 180])
    expect(distributeDisplayWidths([120, 180], 348, 48)).toEqual([120, 180])
    expect(distributeDisplayWidths([120, 180], 0, 48)).toEqual([120, 180])
  })

  it('shares leftover in proportion to base and puts remainder on the last data column', () => {
    // bases 100+200=300, extra 50, viewport 450 → slack 100
    // shares 33 and 67; 100+33=133, 200+67=267; 133+267=400 = 450-50
    expect(distributeDisplayWidths([100, 200], 450, 50)).toEqual([133, 267])
  })

  it('does not give leftover to an empty field list', () => {
    expect(distributeDisplayWidths([], 1000, 48)).toEqual([])
  })
})

describe('invertBaseWidth', () => {
  it('is the identity when the table is overflowing', () => {
    expect(invertBaseWidth(220, 0, [120, 180], 200, 48)).toBe(220)
  })

  it('recovers a base that redistributes back to the dragged display width', () => {
    const bases = [100, 200]
    const displays = distributeDisplayWidths(bases, 450, 50)
    expect(invertBaseWidth(displays[0], 0, bases, 450, 50)).toBe(100)
    const invertedLast = invertBaseWidth(displays[1], 1, bases, 450, 50)
    expect(distributeDisplayWidths([bases[0], invertedLast], 450, 50)[1]).toBe(displays[1])
  })

  it('clamps the persisted base to the shared min/max', () => {
    expect(invertBaseWidth(10, 0, [120, 180], 200, 48)).toBe(COLUMN_WIDTH_MIN)
    expect(invertBaseWidth(900, 0, [120, 180], 200, 48)).toBe(COLUMN_WIDTH_MAX)
  })

  it('leaves a lone data column alone because leftover already fills the viewport', () => {
    expect(invertBaseWidth(800, 0, [120], 900, 48)).toBe(120)
  })
})

describe('headerFitColumnWidth', () => {
  it('never goes below the short-title floor', () => {
    expect(headerFitColumnWidth('A')).toBeGreaterThanOrEqual(HEADER_FIT_MIN)
  })

  it('grows with a longer current-locale label', () => {
    expect(headerFitColumnWidth('Current assignee')).toBeGreaterThan(headerFitColumnWidth('Status'))
  })

  it('raises a short TEXT header to the kind content floor', () => {
    expect(headerFitColumnWidth('ID', 'TEXT')).toBe(KIND_CONTENT_FLOOR.TEXT)
    expect(headerFitColumnWidth('At', 'DATETIME')).toBe(KIND_CONTENT_FLOOR.DATETIME)
  })

  it('keeps a long header when it already exceeds the kind floor', () => {
    expect(headerFitColumnWidth('Current assignee', 'USER'))
      .toBeGreaterThan(KIND_CONTENT_FLOOR.USER)
  })
})
