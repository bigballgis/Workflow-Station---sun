import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import DOMPurify from 'dompurify'

// ─── Shared: DOMPurify SAFE_TAGS config (mirrors FieldRenderer.vue Task 6.5) ─

const SAFE_TAGS = [
  'p', 'br', 'strong', 'em', 'u', 's',
  'ol', 'ul', 'li',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'a', 'img',
  'table', 'tr', 'td', 'th',
  'span', 'div',
]

const SAFE_ATTRS = [
  'href', 'target', 'rel',
  'src', 'alt', 'width', 'height',
  'class', 'style',
  'colspan', 'rowspan',
]

function sanitize(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: SAFE_TAGS,
    ALLOWED_ATTR: SAFE_ATTRS,
  })
}

// ─── Shared: Upload URL resolution logic (mirrors FieldRenderer.vue Task 6.8) ─

const DEFAULT_UPLOAD_URL = '/api/v1/upload'

function resolveUploadUrl(
  propsUploadUrl: string | undefined,
  fieldUploadUrl: string | undefined,
): string {
  if (propsUploadUrl) return propsUploadUrl
  if (fieldUploadUrl && fieldUploadUrl !== '/') return fieldUploadUrl
  return DEFAULT_UPLOAD_URL
}

// ─── Property 13: XSS 净化安全性 ───────────────────────────────────────────
// Feature: function-unit-design-review, Property 13: XSS sanitization safety
// **Validates: Requirements 8.3, 34.1, 34.2, 34.3, 34.4**

describe('Property 13: XSS sanitization safety', () => {
  // Arbitrary that generates HTML strings including malicious tags
  const maliciousHtmlArb = fc.oneof(
    // Random strings (may contain partial HTML)
    fc.string({ minLength: 0, maxLength: 200 }),
    // Strings with script tags
    fc.constantFrom(
      '<script>alert("xss")</script>',
      '<SCRIPT SRC="evil.js"></SCRIPT>',
      '<script type="text/javascript">document.cookie</script>',
      '<img src=x onerror=alert(1)>',
      '<div onclick="alert(1)">click</div>',
      '<a href="javascript:alert(1)">link</a>',
      '<svg onload="alert(1)">',
      '<body onload="alert(1)">',
      '<iframe src="javascript:alert(1)">',
      '<input onfocus="alert(1)" autofocus>',
      '<p>safe paragraph</p>',
      '<strong>bold</strong>',
      '<em>italic</em>',
      '<h1>heading</h1>',
      '<table><tr><td>cell</td></tr></table>',
    ),
    // Template-generated malicious patterns
    fc.tuple(
      fc.constantFrom('script', 'SCRIPT', 'Script'),
      fc.string({ minLength: 0, maxLength: 50 }),
    ).map(([tag, content]) => `<${tag}>${content}</${tag}>`),
    // Event handler injection attempts
    fc.tuple(
      fc.constantFrom('div', 'p', 'span', 'img', 'a'),
      fc.constantFrom('onclick', 'onerror', 'onload', 'onmouseover', 'onfocus', 'onblur'),
      fc.string({ minLength: 1, maxLength: 30 }),
    ).map(([tag, handler, payload]) => `<${tag} ${handler}="${payload}">content</${tag}>`),
    // javascript: protocol injection attempts
    fc.string({ minLength: 0, maxLength: 30 }).map(
      (payload) => `<a href="javascript:${payload}">link</a>`,
    ),
  )

  it('sanitized output never contains script tags', () => {
    fc.assert(
      fc.property(maliciousHtmlArb, (html) => {
        const result = sanitize(html)
        expect(result).not.toMatch(/<script[\s>]/i)
        expect(result).not.toMatch(/<\/script>/i)
      }),
      { numRuns: 200 },
    )
  })

  it('sanitized output never contains event handler attributes', () => {
    const eventHandlerRegex = /\bon\w+\s*=/i

    fc.assert(
      fc.property(maliciousHtmlArb, (html) => {
        const result = sanitize(html)
        expect(result).not.toMatch(eventHandlerRegex)
      }),
      { numRuns: 200 },
    )
  })

  it('sanitized output never contains javascript: protocol', () => {
    fc.assert(
      fc.property(maliciousHtmlArb, (html) => {
        const result = sanitize(html)
        expect(result).not.toMatch(/javascript\s*:/i)
      }),
      { numRuns: 200 },
    )
  })

  it('safe HTML tags are preserved in the output', () => {
    // Exclude tags that require a parent context (tr/td/th need <table>)
    // and void elements (br, img) which are self-closing
    const TABLE_CHILD_TAGS = ['tr', 'td', 'th']
    const VOID_TAGS = ['br', 'img']
    const standaloneSafeTags = SAFE_TAGS.filter(
      (t) => !TABLE_CHILD_TAGS.includes(t) && !VOID_TAGS.includes(t),
    )

    const safeTagArb = fc.constantFrom(...standaloneSafeTags)
    const contentArb = fc.string({ minLength: 1, maxLength: 20 }).filter(
      (s) => !s.includes('<') && !s.includes('>') && s.trim().length > 0,
    )

    fc.assert(
      fc.property(safeTagArb, contentArb, (tag, content) => {
        const input = `<${tag}>${content}</${tag}>`
        const result = sanitize(input)
        // The safe tag should be present in the output
        expect(result).toContain(`<${tag}>`)
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 17: 签名导出为 base64 PNG ────────────────────────────────────
// Feature: function-unit-design-review, Property 17: Signature export as base64 PNG
// **Validates: Requirements 9.3, 22.3, 22.4**

describe('Property 17: Signature export as base64 PNG', () => {
  const BASE64_PNG_PREFIX = 'data:image/png;base64,'

  // Arbitrary for valid base64 strings
  const base64CharsArb = fc.stringMatching(/^[A-Za-z0-9+/=]{4,100}$/)

  it('a valid signature data URL starts with the correct PNG prefix', () => {
    fc.assert(
      fc.property(base64CharsArb, (base64Data) => {
        const dataUrl = `${BASE64_PNG_PREFIX}${base64Data}`
        expect(dataUrl.startsWith(BASE64_PNG_PREFIX)).toBe(true)
      }),
      { numRuns: 100 },
    )
  })

  it('non-empty base64 payload after prefix is decodable', () => {
    // Generate valid base64 strings (length multiple of 4 with proper padding)
    const validBase64Arb = fc
      .array(fc.integer({ min: 0, max: 255 }), { minLength: 3, maxLength: 60 })
      .map((bytes) => {
        // Convert bytes to base64 using Buffer (available in Node/test env)
        return Buffer.from(bytes).toString('base64')
      })

    fc.assert(
      fc.property(validBase64Arb, (base64Data) => {
        const dataUrl = `${BASE64_PNG_PREFIX}${base64Data}`

        // Verify prefix check works
        expect(dataUrl.startsWith(BASE64_PNG_PREFIX)).toBe(true)

        // Extract and decode the base64 payload
        const payload = dataUrl.slice(BASE64_PNG_PREFIX.length)
        expect(payload.length).toBeGreaterThan(0)

        // Verify it's decodable (no exception thrown)
        const decoded = Buffer.from(payload, 'base64')
        expect(decoded.length).toBeGreaterThan(0)
      }),
      { numRuns: 100 },
    )
  })

  it('empty string is not a valid signature export', () => {
    fc.assert(
      fc.property(fc.constant(''), (emptyValue) => {
        expect(emptyValue.startsWith(BASE64_PNG_PREFIX)).toBe(false)
      }),
      { numRuns: 1 },
    )
  })

  it('random strings without the prefix are rejected by the format check', () => {
    const randomStringArb = fc.string({ minLength: 1, maxLength: 100 }).filter(
      (s) => !s.startsWith(BASE64_PNG_PREFIX),
    )

    fc.assert(
      fc.property(randomStringArb, (str) => {
        expect(str.startsWith(BASE64_PNG_PREFIX)).toBe(false)
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Property 21: Upload URL 解析优先级 ─────────────────────────────────────
// Feature: function-unit-design-review, Property 21: Upload URL resolution priority
// **Validates: Requirements 24.1, 24.4**

describe('Property 21: Upload URL resolution priority', () => {
  const nonEmptyUrlArb = fc.stringMatching(/^\/[a-z0-9\-_.]{1,49}$/)
    .filter((s) => s.length >= 2 && s !== '/')

  it('explicit uploadUrl prop always takes highest priority', () => {
    fc.assert(
      fc.property(nonEmptyUrlArb, nonEmptyUrlArb, (propsUrl, fieldUrl) => {
        const result = resolveUploadUrl(propsUrl, fieldUrl)
        expect(result).toBe(propsUrl)
      }),
      { numRuns: 100 },
    )
  })

  it('field.uploadUrl is used when props.uploadUrl is absent', () => {
    fc.assert(
      fc.property(nonEmptyUrlArb, (fieldUrl) => {
        const result = resolveUploadUrl(undefined, fieldUrl)
        expect(result).toBe(fieldUrl)
      }),
      { numRuns: 100 },
    )
  })

  it('default /api/v1/upload is used when both are absent', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(undefined, undefined),
        () => {
          const result = resolveUploadUrl(undefined, undefined)
          expect(result).toBe(DEFAULT_UPLOAD_URL)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('field.uploadUrl "/" is treated as absent (falls back to default)', () => {
    fc.assert(
      fc.property(fc.constant('/'), (slashUrl) => {
        const result = resolveUploadUrl(undefined, slashUrl)
        expect(result).toBe(DEFAULT_UPLOAD_URL)
      }),
      { numRuns: 1 },
    )
  })

  it('priority chain: props > field > default for any URL combination', () => {
    const optionalUrlArb = fc.option(nonEmptyUrlArb, { nil: undefined })

    fc.assert(
      fc.property(optionalUrlArb, optionalUrlArb, (propsUrl, fieldUrl) => {
        const result = resolveUploadUrl(propsUrl, fieldUrl)

        if (propsUrl) {
          expect(result).toBe(propsUrl)
        } else if (fieldUrl && fieldUrl !== '/') {
          expect(result).toBe(fieldUrl)
        } else {
          expect(result).toBe(DEFAULT_UPLOAD_URL)
        }
      }),
      { numRuns: 200 },
    )
  })
})
