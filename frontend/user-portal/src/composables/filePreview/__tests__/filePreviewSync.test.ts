import { describe, expect, it, vi } from 'vitest'
import {
  FILE_PREVIEW_STORAGE_KEY,
  FILE_PREVIEW_WINDOW_NAME,
  filePreviewWindowFeatures,
  parseFilePreviewSnapshot,
  readStoredPreviewSnapshot,
  tryOpenPreviewWindow,
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

  it('asks the browser for a separate preview window', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue({ closed: false } as Window)
    expect(tryOpenPreviewWindow()).toBe(true)
    const features = filePreviewWindowFeatures()
    expect(features.startsWith('popup=yes,')).toBe(true)
    expect(features).not.toContain('noopener')
    expect(open).toHaveBeenCalledWith(expect.stringContaining('/file-preview'), FILE_PREVIEW_WINDOW_NAME, features)
    open.mockRestore()
  })
})
