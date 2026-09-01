import { describe, expect, it } from 'vitest'
import {
  boundSpreadsheetMatrix,
  classifyBlobPreview,
  resolveFilePreviewKind,
  TABLE_MAX_COLS,
  TABLE_MAX_ROWS,
} from '../filePreview'
import { decodeTextPreview, TEXT_CHAR_LIMIT } from '../filePreviewText'
import { extractDocPreviewText } from '../filePreviewDoc'

function bytesOf(text: string): Uint8Array {
  return new TextEncoder().encode(text)
}

describe('resolveFilePreviewKind', () => {
  it('classifies images from mime and extension', () => {
    expect(resolveFilePreviewKind('a.jpg', 'image/jpeg')).toBe('image')
    expect(resolveFilePreviewKind('photo.PNG')).toBe('image')
  })

  it('classifies tiff from mime before generic image/*', () => {
    expect(resolveFilePreviewKind('scan.tiff')).toBe('tiff')
    expect(resolveFilePreviewKind('scan', 'image/tiff')).toBe('tiff')
  })

  it('shows svg and other text-like types as text, not as HTML/image', () => {
    expect(resolveFilePreviewKind('x.svg', 'image/svg+xml')).toBe('text')
    expect(resolveFilePreviewKind('notes.md')).toBe('text')
    expect(resolveFilePreviewKind('data.json')).toBe('text')
    expect(resolveFilePreviewKind('sheet.csv')).toBe('text')
    expect(resolveFilePreviewKind('export.csv', 'text/csv')).toBe('text')
  })

  it('classifies pdf, csv, office, and fail-closed types', () => {
    expect(resolveFilePreviewKind('doc.pdf', 'application/pdf')).toBe('pdf')
    expect(resolveFilePreviewKind('notes.txt')).toBe('text')
    expect(resolveFilePreviewKind('sheet.xlsx')).toBe('spreadsheet')
    expect(resolveFilePreviewKind('old.xls')).toBe('spreadsheet')
    expect(resolveFilePreviewKind('memo.docx')).toBe('docx')
    expect(resolveFilePreviewKind('memo.doc')).toBe('doc')
    expect(resolveFilePreviewKind('deck.pptx')).toBe('pptx')
    expect(resolveFilePreviewKind('deck.ppt')).toBe('unsupported')
    expect(resolveFilePreviewKind('pack.zip')).toBe('unsupported')
    expect(resolveFilePreviewKind('pack.rar')).toBe('unsupported')
    expect(resolveFilePreviewKind('mail.msg')).toBe('unsupported')
  })

  it('rejects extension/magic mismatches', () => {
    expect(resolveFilePreviewKind('a.pdf', undefined, bytesOf('not-a-pdf'))).toBe('unsupported')
    const jpeg = new Uint8Array([0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0])
    expect(resolveFilePreviewKind('a.jpg', undefined, jpeg)).toBe('image')
    const pdfHeader = bytesOf('%PDF-1.7')
    const bom = new Uint8Array([0xEF, 0xBB, 0xBF, ...pdfHeader])
    expect(resolveFilePreviewKind('a.pdf', undefined, bom)).toBe('pdf')
    const junk = new Uint8Array(80)
    junk.set(pdfHeader, 40)
    expect(resolveFilePreviewKind('a.pdf', undefined, junk)).toBe('pdf')
    expect(resolveFilePreviewKind('report', 'application/pdf', pdfHeader)).toBe('pdf')
    const pk = new Uint8Array([0x50, 0x4B, 0x03, 0x04, ...bytesOf('hello zip')])
    expect(resolveFilePreviewKind('a.docx', undefined, pk)).toBe('unsupported')
    const docx = new Uint8Array([0x50, 0x4B, 0x03, 0x04, ...bytesOf('word/document.xml')])
    expect(resolveFilePreviewKind('a.docx', undefined, docx)).toBe('docx')
    expect(resolveFilePreviewKind('a.zip', undefined, docx)).toBe('unsupported')
  })
})

describe('classifyBlobPreview', () => {
  it('reads a prefix from the blob', async () => {
    const blob = new Blob([new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2D, 0x31])], { type: 'application/pdf' })
    expect(await classifyBlobPreview('a.pdf', blob)).toBe('pdf')
  })
})

describe('boundSpreadsheetMatrix', () => {
  it('caps excel preview at 1000 rows by 80 cols', () => {
    const wide = [Array.from({ length: TABLE_MAX_COLS + 2 }, (_, i) => `c${i}`)]
    const tall = Array.from({ length: TABLE_MAX_ROWS + 5 }, () => ['x'])
    const boundedWide = boundSpreadsheetMatrix(wide)
    const boundedTall = boundSpreadsheetMatrix(tall)
    expect(boundedWide.rows[0]?.length).toBe(TABLE_MAX_COLS)
    expect(boundedWide.truncated).toBe(true)
    expect(boundedTall.rows.length).toBe(TABLE_MAX_ROWS)
    expect(boundedTall.truncated).toBe(true)
  })
})

describe('decodeTextPreview', () => {
  it('truncates at the character cap', () => {
    const small = decodeTextPreview(bytesOf('hello').buffer)
    expect(small).toEqual({ text: 'hello', truncated: false })
    const big = decodeTextPreview(bytesOf('x'.repeat(TEXT_CHAR_LIMIT + 10)).buffer)
    expect(big.truncated).toBe(true)
    expect(big.text.length).toBe(TEXT_CHAR_LIMIT)
  })
})

describe('extractDocPreviewText', () => {
  it('pulls UTF-16LE readable runs', () => {
    const hello = 'HelloDoc'
    const buf = new Uint8Array(hello.length * 2)
    for (let i = 0; i < hello.length; i++) {
      buf[i * 2] = hello.charCodeAt(i)
      buf[i * 2 + 1] = 0
    }
    expect(extractDocPreviewText(buf.buffer).text).toContain('HelloDoc')
  })
})
