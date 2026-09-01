import { describe, expect, it } from 'vitest'
import { selectTiffDisplayPages } from '../filePreviewTiff'

describe('selectTiffDisplayPages', () => {
  it('drops a thumbnail IFD so the full-size page is the default', () => {
    const pages = selectTiffDisplayPages([
      { t256: [160], t257: [120] },
      { t256: [2400], t257: [1800] },
    ])
    expect(pages).toEqual([{ ifdIndex: 1, width: 2400, height: 1800 }])
  })

  it('keeps similarly sized pages and still drops a thumbnail', () => {
    const pages = selectTiffDisplayPages([
      { width: 2400, height: 1800 },
      { width: 2400, height: 1800 },
      { width: 160, height: 120 },
    ])
    expect(pages.map((p) => p.ifdIndex)).toEqual([0, 1])
  })

  it('skips IFDs without a positive size', () => {
    expect(selectTiffDisplayPages([{ t256: [0], t257: [0] }, {}])).toEqual([])
  })
})
