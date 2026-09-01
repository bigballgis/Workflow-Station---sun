import { describe, expect, it } from 'vitest'
import {
  FILE_PREVIEW_STORAGE_KEY,
  parseFilePreviewSnapshot,
  readStoredPreviewSnapshot,
  writeStoredPreviewSnapshot,
} from '../filePreviewSync'

describe('filePreviewSync', () => {
  it('rejects snapshots without a url', () => {
    expect(parseFilePreviewSnapshot(null)).toBeNull()
    expect(parseFilePreviewSnapshot({ name: 'a.pdf' })).toBeNull()
    expect(parseFilePreviewSnapshot({ url: '' })).toBeNull()
  })

  it('keeps only items that have a url', () => {
    const parsed = parseFilePreviewSnapshot({
      url: '/a.pdf',
      name: 'a.pdf',
      index: 1,
      items: [{ url: '/a.pdf', name: 'a.pdf' }, { name: 'skip' }, { url: '/b.pdf', name: 'b.pdf' }],
    })
    expect(parsed?.items?.map((item) => item.url)).toEqual(['/a.pdf', '/b.pdf'])
    expect(parsed?.index).toBe(1)
  })

  it('round-trips a snapshot through localStorage', () => {
    localStorage.removeItem(FILE_PREVIEW_STORAGE_KEY)
    writeStoredPreviewSnapshot({ url: '/a.pdf', name: 'a.pdf', index: 0, items: [{ url: '/a.pdf', name: 'a.pdf' }] })
    expect(readStoredPreviewSnapshot()?.url).toBe('/a.pdf')
    localStorage.removeItem(FILE_PREVIEW_STORAGE_KEY)
  })
})
