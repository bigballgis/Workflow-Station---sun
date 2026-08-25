import { describe, expect, it } from 'vitest'
import {
  EMAIL_BODY_FONT,
  insertAtCursor,
  isSwitchToVisual,
  sanitizeEmailBodyHtml,
  wrapEmailPreviewDocument,
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

  it('only confirms when switching html to visual', () => {
    expect(isSwitchToVisual('html', 'visual')).toBe(true)
    expect(isSwitchToVisual('visual', 'html')).toBe(false)
    expect(isSwitchToVisual('visual', 'visual')).toBe(false)
  })

  it('inserts a token at the cursor', () => {
    expect(insertAtCursor('ab', '${to}', 1, 1)).toBe('a${to}b')
    expect(insertAtCursor('ab', '${to}', 0, 2)).toBe('${to}')
  })
})
