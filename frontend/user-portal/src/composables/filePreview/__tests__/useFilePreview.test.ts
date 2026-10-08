import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FILE_PREVIEW_STORAGE_KEY } from '../filePreviewSync'
import {
  closeFilePreview,
  hydrateFilePreviewFromStorage,
  openFilePreview,
  openFilePreviewFromList,
  showFilePreviewAt,
  useFilePreviewState,
} from '../useFilePreview'

describe('file preview playlist', () => {
  beforeEach(() => {
    vi.spyOn(window, 'open').mockReturnValue(null)
  })

  afterEach(() => {
    closeFilePreview()
    localStorage.removeItem(FILE_PREVIEW_STORAGE_KEY)
    vi.restoreAllMocks()
  })

  it('opens a single file without a list', () => {
    openFilePreview({ url: '/a.pdf', name: 'a.pdf' })
    const state = useFilePreviewState()
    expect(state.visible).toBe(true)
    expect(state.name).toBe('a.pdf')
    expect(state.items).toHaveLength(1)
    closeFilePreview()
    expect(state.visible).toBe(false)
  })

  it('moves to the next file without closing', () => {
    openFilePreviewFromList(
      { url: '/a.pdf', name: 'a.pdf' },
      [
        { url: '/a.pdf', name: 'a.pdf' },
        { url: '/b.pdf', name: 'b.pdf' },
      ],
    )
    const state = useFilePreviewState()
    expect(state.index).toBe(0)
    showFilePreviewAt(1)
    expect(state.visible).toBe(true)
    expect(state.name).toBe('b.pdf')
    expect(state.url).toBe('/b.pdf')
    closeFilePreview()
  })

  it('prepends the current file when it is missing from the list', () => {
    openFilePreviewFromList(
      { url: '/x.pdf', name: 'x.pdf' },
      [{ url: '/a.pdf', name: 'a.pdf' }],
    )
    const state = useFilePreviewState()
    expect(state.items.map((i) => i.url)).toEqual(['/x.pdf', '/a.pdf'])
    expect(state.index).toBe(0)
    closeFilePreview()
  })

  it('keeps cannotDownload from the clicked field when the playlist copy omitted it', () => {
    openFilePreviewFromList(
      { url: '/line.pdf', name: 'line.pdf', cannotDownload: true },
      [{ url: '/line.pdf', name: 'line.pdf' }],
    )
    const state = useFilePreviewState()
    expect(state.cannotDownload).toBe(true)
    expect(state.items[0].cannotDownload).toBe(true)
    closeFilePreview()
  })

  it('keeps cannotDownload when the playlist already blocked the same URL', () => {
    openFilePreviewFromList(
      { url: '/line.pdf', name: 'line.pdf' },
      [{ url: '/line.pdf', name: 'line.pdf', cannotDownload: true }],
    )
    const state = useFilePreviewState()
    expect(state.cannotDownload).toBe(true)
    closeFilePreview()
  })

  it('leaves the form uncovered when a preview window opens', () => {
    vi.mocked(window.open).mockReturnValue({ closed: false } as Window)
    openFilePreview({ url: '/a.pdf', name: 'a.pdf' })
    const state = useFilePreviewState()
    expect(state.visible).toBe(false)
    expect(state.name).toBe('a.pdf')
    expect(window.open).toHaveBeenCalledWith(
      expect.stringContaining(`id=${state.previewId}`),
      '_blank',
      expect.stringContaining('popup=yes'),
    )
    expect(localStorage.getItem(FILE_PREVIEW_STORAGE_KEY)).toContain('/a.pdf')
  })

  it('opens a second file in another window and keeps the first snapshot', () => {
    vi.mocked(window.open).mockReturnValue({ closed: false } as Window)
    openFilePreview({ url: '/a.pdf', name: 'a.pdf' })
    const idA = useFilePreviewState().previewId
    openFilePreview({ url: '/b.pdf', name: 'b.pdf' })
    const idB = useFilePreviewState().previewId
    expect(idA).not.toBe(idB)
    expect(window.open).toHaveBeenCalledTimes(2)
    expect(hydrateFilePreviewFromStorage(idA)).toBe(true)
    expect(useFilePreviewState().url).toBe('/a.pdf')
    expect(hydrateFilePreviewFromStorage(idB)).toBe(true)
    expect(useFilePreviewState().url).toBe('/b.pdf')
  })

  it('hydrates the preview page from its own snapshot id', () => {
    vi.mocked(window.open).mockReturnValue({ closed: false } as Window)
    openFilePreview({ url: '/a.pdf', name: 'a.pdf' })
    const id = useFilePreviewState().previewId
    const state = useFilePreviewState()
    state.url = ''
    state.name = ''
    state.visible = false
    expect(hydrateFilePreviewFromStorage(id)).toBe(true)
    expect(state.url).toBe('/a.pdf')
    expect(state.visible).toBe(true)
    expect(hydrateFilePreviewFromStorage(null)).toBe(false)
  })
})
