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

  it('leaves the form uncovered when a preview window opens', () => {
    vi.mocked(window.open).mockReturnValue({ closed: false } as Window)
    openFilePreview({ url: '/a.pdf', name: 'a.pdf' })
    const state = useFilePreviewState()
    expect(state.visible).toBe(false)
    expect(state.name).toBe('a.pdf')
    expect(window.open).toHaveBeenCalled()
    expect(localStorage.getItem(FILE_PREVIEW_STORAGE_KEY)).toContain('/a.pdf')
  })

  it('hydrates the preview page from the stored snapshot', () => {
    vi.mocked(window.open).mockReturnValue({ closed: false } as Window)
    openFilePreview({ url: '/a.pdf', name: 'a.pdf' })
    const state = useFilePreviewState()
    state.url = ''
    state.name = ''
    state.visible = false
    expect(hydrateFilePreviewFromStorage()).toBe(true)
    expect(state.url).toBe('/a.pdf')
    expect(state.visible).toBe(true)
  })
})
