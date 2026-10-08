import { describe, expect, it, vi } from 'vitest'
import {
  FILE_PREVIEW_STORAGE_KEY,
  filePreviewWindowFeatures,
  newFilePreviewId,
  parseFilePreviewSnapshot,
  previewIdFromLocation,
  readStoredPreviewSnapshot,
  tryOpenPreviewWindow,
  writeStoredPreviewSnapshot,
} from '../filePreviewSync'

const ID_A = '11111111-1111-4111-8111-111111111111'
const ID_B = '22222222-2222-4222-8222-222222222222'

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

  it('round-trips one snapshot without replacing another', () => {
    localStorage.removeItem(FILE_PREVIEW_STORAGE_KEY)
    writeStoredPreviewSnapshot(ID_A, { url: '/a.pdf', name: 'a.pdf', index: 0, items: [{ url: '/a.pdf', name: 'a.pdf' }] })
    writeStoredPreviewSnapshot(ID_B, { url: '/b.pdf', name: 'b.pdf', index: 0, items: [{ url: '/b.pdf', name: 'b.pdf' }] })
    expect(readStoredPreviewSnapshot(ID_A)?.url).toBe('/a.pdf')
    expect(readStoredPreviewSnapshot(ID_B)?.url).toBe('/b.pdf')
    localStorage.removeItem(FILE_PREVIEW_STORAGE_KEY)
  })

  it('reads the preview id from the window address', () => {
    expect(previewIdFromLocation(`?id=${ID_A}`)).toBe(ID_A)
    expect(previewIdFromLocation('')).toBeNull()
    expect(previewIdFromLocation('?id=not-a-uuid')).toBeNull()
  })

  it('opens each preview in its own window', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue({ closed: false } as Window)
    const id = newFilePreviewId()
    expect(tryOpenPreviewWindow(id)).toBe(true)
    const features = filePreviewWindowFeatures()
    expect(features.startsWith('popup=yes,')).toBe(true)
    expect(features).not.toContain('noopener')
    expect(open).toHaveBeenCalledWith(expect.stringContaining(`id=${id}`), '_blank', features)
    open.mockRestore()
  })
})
