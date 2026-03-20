import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * Helper: the sanitization pipeline used by MarkdownRenderer.vue
 * Tests the logic directly without Vue component mounting.
 */
function renderMarkdown(content: string): string {
  const rawHtml = marked.parse(content) as string
  return DOMPurify.sanitize(rawHtml)
}

describe('Feature: xml-document-viewer', () => {
  /**
   * Property 2: XSS Safe Filtering
   *
   * For any Markdown input containing <script> tags, onerror/onclick event attributes,
   * or javascript: protocol links, the rendered HTML output should contain none of them.
   *
   * Validates: Requirements 2.3
   */
  it('Property 2: XSS Safe Filtering', () => {
    const safeText = fc.string({ minLength: 1, maxLength: 30 }).filter(s => s.trim().length > 0)

    const xssPayload = fc.oneof(
      // <script> tag payloads
      safeText.map(t => `<script>${t}</script>`),
      safeText.map(t => `<script src="${t}"></script>`),
      safeText.map(t => `<SCRIPT>${t}</SCRIPT>`),
      // onerror attribute payloads
      safeText.map(t => `<img src="x" onerror="${t}">`),
      safeText.map(t => `<img onerror="${t}" src="x">`),
      safeText.map(t => `<div onerror="${t}">text</div>`),
      // onclick attribute payloads
      safeText.map(t => `<div onclick="${t}">click me</div>`),
      safeText.map(t => `<a onclick="${t}">link</a>`),
      // javascript: protocol payloads
      safeText.map(t => `<a href="javascript:${t}">link</a>`),
      safeText.map(t => `[link](javascript:${t})`),
      // Mixed payloads
      safeText.map(t => `<img src="javascript:${t}">`),
      safeText.map(t => `<iframe src="javascript:${t}"></iframe>`)
    )

    const markdownWithXss = fc.tuple(safeText, xssPayload, safeText).map(
      ([before, payload, after]) => `${before}\n\n${payload}\n\n${after}`
    )

    fc.assert(
      fc.property(markdownWithXss, (markdown) => {
        const html = renderMarkdown(markdown)
        const htmlLower = html.toLowerCase()

        // No <script> tags (actual tags, not escaped text)
        expect(htmlLower).not.toMatch(/<script[\s>]/)
        expect(htmlLower).not.toMatch(/<\/script>/)

        // No event handler attributes on actual HTML elements
        expect(htmlLower).not.toMatch(/<[^>]+\bon\w+\s*=/)

        // No javascript: protocol in href/src attributes of actual HTML elements
        expect(htmlLower).not.toMatch(/<[^>]+(?:href|src)\s*=\s*["']?\s*javascript:/i)
      }),
      { numRuns: 100 }
    )
  })
})
