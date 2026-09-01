import { afterEach, describe, expect, it } from 'vitest'
import {
  EMAIL_BODY_FONT,
  emailBodyEditorModeStorageKey,
  htmlFromVisualEditor,
  insertAtCursor,
  isSwitchToVisual,
  parseEmailBodyEditorMode,
  readSavedEmailBodyEditorMode,
  sanitizeEmailBodyHtml,
  wrapEmailPreviewDocument,
  writeSavedEmailBodyEditorMode,
} from '../emailPreviewShell'

describe('emailPreviewShell', () => {
  it('keeps ${variable} tokens after sanitize', () => {
    const html = '<p>this email ${to}</p>'
    expect(sanitizeEmailBodyHtml(html)).toContain('${to}')
    expect(wrapEmailPreviewDocument(html)).toContain('${to}')
  })

  it('strips script tags from preview html', () => {
    const html = '<p>ok</p><script>alert(1)</script>'
    const out = sanitizeEmailBodyHtml(html)
    expect(out).not.toContain('script')
    expect(out).toContain('ok')
  })

  it('wraps preview in an email-safe font stack', () => {
    const doc = wrapEmailPreviewDocument('<p>Hello</p>')
    expect(doc).toContain('width="100%"')
    expect(doc).toContain(EMAIL_BODY_FONT)
    expect(doc).toContain('<p>Hello</p>')
  })

  it('keeps table markup and inline styles', () => {
    const html = '<table style="border:1px solid #ccc"><tr><td>A</td></tr></table>'
    const out = sanitizeEmailBodyHtml(html)
    expect(out).toContain('<table')
    expect(out).toContain('border:1px solid #ccc')
    expect(out).toContain('A')
  })

  it('keeps a leading style tag and class-based table CSS', () => {
    const html =
      '<style type="text/css">.ws-table th{background:#1e3a5f;color:#fff}</style>' +
      '<table class="ws-table"><tr><th>Item</th></tr></table>'
    const out = sanitizeEmailBodyHtml(html)
    expect(out).toMatch(/<style\b/i)
    expect(out).toContain('.ws-table th')
    expect(out).toContain('background:#1e3a5f')
    expect(out).toContain('class="ws-table"')
    expect(wrapEmailPreviewDocument(html)).toMatch(/<style\b/i)
  })

  it('still strips script and stylesheet links when style tags are allowed', () => {
    const html =
      '<style>.ok{color:red}</style>' +
      '<script>alert(1)</script>' +
      '<link rel="stylesheet" href="https://evil.example/x.css">' +
      '<p onclick="alert(1)">ok</p>'
    const out = sanitizeEmailBodyHtml(html)
    expect(out).toContain('.ok{color:red}')
    expect(out).not.toMatch(/<script/i)
    expect(out).not.toContain('alert(1)')
    expect(out).not.toMatch(/<link/i)
    expect(out).not.toContain('onclick')
    expect(out).toContain('ok')
  })

  it('only confirms when switching html to visual', () => {
    expect(isSwitchToVisual('html', 'visual')).toBe(true)
    expect(isSwitchToVisual('visual', 'html')).toBe(false)
    expect(isSwitchToVisual('visual', 'visual')).toBe(false)
  })

  it('inserts a token at the cursor', () => {
    expect(insertAtCursor('ab', '${to}', 1, 1)).toBe('a${to}b')
    expect(insertAtCursor('ab', '${to}', 0, 2)).toBe('${to}')
  })

  it('reads serialized HTML from a Visual editor after parse', () => {
    expect(htmlFromVisualEditor({ getHtml: () => '<p>flattened</p>' })).toBe('<p>flattened</p>')
    expect(htmlFromVisualEditor(null)).toBeNull()
    expect(htmlFromVisualEditor(undefined)).toBeNull()
  })
})

describe('saved email body editor mode', () => {
  const fuId = 91
  const templateId = 17
  const otherTemplateId = 18

  afterEach(() => {
    localStorage.removeItem(emailBodyEditorModeStorageKey(fuId, templateId))
    localStorage.removeItem(emailBodyEditorModeStorageKey(fuId, otherTemplateId))
  })

  it('parses only html as html; everything else is visual', () => {
    expect(parseEmailBodyEditorMode('html')).toBe('html')
    expect(parseEmailBodyEditorMode('visual')).toBe('visual')
    expect(parseEmailBodyEditorMode('HTML')).toBe('visual')
    expect(parseEmailBodyEditorMode(null)).toBe('visual')
    expect(parseEmailBodyEditorMode(undefined)).toBe('visual')
  })

  it('returns visual when nothing has been saved', () => {
    expect(readSavedEmailBodyEditorMode(fuId, templateId)).toBe('visual')
  })

  it('returns visual for invalid template ids without writing', () => {
    writeSavedEmailBodyEditorMode(fuId, 0, 'html')
    writeSavedEmailBodyEditorMode(fuId, -1, 'html')
    expect(readSavedEmailBodyEditorMode(fuId, 0)).toBe('visual')
    expect(readSavedEmailBodyEditorMode(fuId, -1)).toBe('visual')
    expect(localStorage.getItem(emailBodyEditorModeStorageKey(fuId, 0))).toBeNull()
  })

  it('reads back the mode written after save', () => {
    writeSavedEmailBodyEditorMode(fuId, templateId, 'html')
    expect(readSavedEmailBodyEditorMode(fuId, templateId)).toBe('html')
    writeSavedEmailBodyEditorMode(fuId, templateId, 'visual')
    expect(readSavedEmailBodyEditorMode(fuId, templateId)).toBe('visual')
  })

  it('does not leak a saved mode to another template', () => {
    writeSavedEmailBodyEditorMode(fuId, templateId, 'html')
    expect(readSavedEmailBodyEditorMode(fuId, otherTemplateId)).toBe('visual')
  })

  it('treats an unknown stored value as visual', () => {
    localStorage.setItem(emailBodyEditorModeStorageKey(fuId, templateId), 'source')
    expect(readSavedEmailBodyEditorMode(fuId, templateId)).toBe('visual')
  })
})
