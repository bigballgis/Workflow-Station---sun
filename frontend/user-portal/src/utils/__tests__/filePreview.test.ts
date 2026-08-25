import { describe, expect, it } from 'vitest'
import { isCannotDownload, resolveFilePreviewKind, uploadPropsBlockDownload } from '../filePreview'

describe('resolveFilePreviewKind', () => {
  it('classifies images from mime and extension', () => {
    expect(resolveFilePreviewKind('a.jpg', 'image/jpeg')).toBe('image')
    expect(resolveFilePreviewKind('photo.PNG')).toBe('image')
    expect(resolveFilePreviewKind('x.svg', 'image/svg+xml')).toBe('unsupported')
  })

  it('classifies pdf and text, and falls back for office files', () => {
    expect(resolveFilePreviewKind('doc.pdf', 'application/pdf')).toBe('pdf')
    expect(resolveFilePreviewKind('notes.txt')).toBe('text')
    expect(resolveFilePreviewKind('sheet.xlsx')).toBe('unsupported')
  })
})

describe('isCannotDownload', () => {
  it('is off unless the designer switch is explicitly true', () => {
    expect(isCannotDownload(undefined)).toBe(false)
    expect(isCannotDownload(false)).toBe(false)
    expect(isCannotDownload(true)).toBe(true)
    expect(isCannotDownload('true')).toBe(true)
    expect(isCannotDownload(1)).toBe(true)
  })
})

describe('uploadPropsBlockDownload', () => {
  it('honors cannotDownload and native canNotDownload', () => {
    expect(uploadPropsBlockDownload(undefined)).toBe(false)
    expect(uploadPropsBlockDownload({ cannotDownload: true })).toBe(true)
    expect(uploadPropsBlockDownload({ canNotDownload: true })).toBe(true)
  })
})
