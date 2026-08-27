import { describe, expect, it } from 'vitest'
import {
  CELL_PADDING_X_PX,
  HEADER_CARET_PX,
  HEADER_CHROME_PX,
  HEADER_FIT_MIN,
  HEADER_FIT_PAD_PX,
  HEADER_HANDLE_GUTTER_PX,
  HEADER_TRIGGER_GAP_PX,
  KIND_CONTENT_FLOOR,
  headerFitColumnWidth,
} from '../columnWidthLayout'

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

  it('keeps chrome compact so the caret sits next to the drag line', () => {
    expect(HEADER_HANDLE_GUTTER_PX).toBe(4)
    expect(CELL_PADDING_X_PX).toBe(6)
    expect(HEADER_FIT_PAD_PX).toBe(6)
    expect(HEADER_CHROME_PX).toBe(
      HEADER_CARET_PX
        + HEADER_TRIGGER_GAP_PX
        + HEADER_HANDLE_GUTTER_PX
        + CELL_PADDING_X_PX * 2
        + HEADER_FIT_PAD_PX,
    )
  })

  it('gives Current Assignee more than the USER content floor so the header is not ellipsized', () => {
    expect(headerFitColumnWidth('Current Assignee', 'USER')).toBeGreaterThan(KIND_CONTENT_FLOOR.USER)
    expect(headerFitColumnWidth('Process Title', 'TEXT')).toBeGreaterThanOrEqual(KIND_CONTENT_FLOOR.TEXT)
  })

  it('gives long English titles more room than a USER/ENUM content floor', () => {
    expect(headerFitColumnWidth('Entity Manager', 'USER')).toBeGreaterThan(KIND_CONTENT_FLOOR.USER)
    expect(headerFitColumnWidth('Function Manager', 'USER')).toBeGreaterThan(KIND_CONTENT_FLOOR.USER)
    expect(headerFitColumnWidth('Assignment Type', 'ENUM')).toBeGreaterThan(KIND_CONTENT_FLOOR.ENUM)
  })
})
