import { describe, expect, it } from 'vitest'
import {
  DEFAULT_UPLOAD_MAX_FILES,
  extractStoredUploadUrl,
  formatUploadCellText,
  joinTargetFileNames,
  persistFromUploadFileList,
  persistUploadValue,
  resolveUploadMaxFiles,
  uploadValueFingerprint,
} from '../uploadFieldValue'

describe('resolveUploadMaxFiles', () => {
  it('defaults legacy generator limit:1 + multiple:false to 10', () => {
    expect(resolveUploadMaxFiles({ multiple: false, limit: 1 })).toBe(DEFAULT_UPLOAD_MAX_FILES)
    expect(resolveUploadMaxFiles({})).toBe(DEFAULT_UPLOAD_MAX_FILES)
    expect(resolveUploadMaxFiles(null)).toBe(DEFAULT_UPLOAD_MAX_FILES)
  })

  it('honors explicit maxFiles including 1', () => {
    expect(resolveUploadMaxFiles({ maxFiles: 1, multiple: false, limit: 1 })).toBe(1)
    expect(resolveUploadMaxFiles({ maxFiles: 5 })).toBe(5)
  })

  it('honors multiple:true + limit when maxFiles is absent', () => {
    expect(resolveUploadMaxFiles({ multiple: true, limit: 3 })).toBe(3)
  })
})

describe('persistUploadValue', () => {
  const files = [
    { url: '/api/v1/upload/files/a.pdf?originalName=a.pdf', name: 'a.pdf' },
    { url: '/api/v1/upload/files/b.pdf?originalName=b.pdf', name: 'b.pdf' },
  ]

  it('writes a single URL string when maxFiles is 1', () => {
    expect(persistUploadValue(files, 1)).toBe(files[0].url)
  })

  it('writes {url,name}[] when maxFiles is greater than 1', () => {
    expect(persistUploadValue(files, 10)).toEqual(files)
  })
})

describe('persistFromUploadFileList', () => {
  it('keeps every successful file and ignores in-flight ones', () => {
    const stored = persistFromUploadFileList(
      [
        { status: 'success', url: '/api/v1/upload/files/a?originalName=a.pdf', name: 'a.pdf' },
        { status: 'uploading', url: '', name: 'b.pdf' },
        {
          status: 'success',
          response: { data: { url: '/api/v1/upload/files/c?originalName=c.pdf' } },
          name: 'c.pdf',
        },
      ],
      10,
    )
    expect(stored).toEqual([
      { url: '/api/v1/upload/files/a?originalName=a.pdf', name: 'a.pdf' },
      { url: '/api/v1/upload/files/c?originalName=c.pdf', name: 'c.pdf' },
    ])
  })
})

describe('cell helpers', () => {
  it('joins companion names and fingerprints URLs', () => {
    expect(joinTargetFileNames([{ name: 'a.pdf' }, { name: 'b.docx' }])).toBe('a.pdf; b.docx')
    const value = [
      { url: '/api/v1/upload/files/a?originalName=a.pdf', name: 'a.pdf' },
      { url: '/api/v1/upload/files/b?originalName=b.pdf', name: 'b.pdf' },
    ]
    expect(formatUploadCellText(value).text).toBe('a.pdf +1')
    expect(uploadValueFingerprint(value)).toBe(
      '/api/v1/upload/files/a?originalName=a.pdf\0/api/v1/upload/files/b?originalName=b.pdf',
    )
  })

  it('reads nested ApiResponse urls', () => {
    expect(extractStoredUploadUrl({ data: { url: '/api/v1/upload/files/x' } })).toBe(
      '/api/v1/upload/files/x',
    )
  })
})
