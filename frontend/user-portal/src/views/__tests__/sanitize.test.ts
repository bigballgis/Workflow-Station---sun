/**
 * Property 11: DOMPurify SVG 净化安全性
 * **Validates: Requirements 24.1, 24.2**
 *
 * For any input string containing random HTML/SVG tags, after DOMPurify
 * sanitization the output should not contain dangerous content like
 * <script>, onerror, onclick, javascript: etc., while valid SVG tags
 * should be preserved.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { sanitizeSvgIcon } from '@/utils/sanitizeSvgIcon'

function sanitizeIcon(icon: string): string {
  return sanitizeSvgIcon(icon)
}

// ─── Arbitraries ─────────────────────────────────────────────────────────────

const dangerousPayloads = [
  '<script>alert(1)</script>',
  '<img onerror="alert(1)" src="x">',
  '<div onclick="alert(1)">click</div>',
  '<a href="javascript:alert(1)">link</a>',
  '<svg onload="alert(1)"><path d="M0 0"/></svg>',
  '<iframe src="javascript:alert(1)"></iframe>',
  '<body onload="alert(1)">',
  '<input onfocus="alert(1)" autofocus>',
  '<marquee onstart="alert(1)">',
  '<style>body{background:red}</style>',
]

const maliciousInputArb = fc.oneof(
  fc.constantFrom(...dangerousPayloads),
  fc.string().map(s => `<script>${s}</script>`),
  fc.string().map(s => `<img onerror="${s}" src="x">`),
  fc.string().map(s => `<svg onload="${s}"><path d="M0 0"/></svg>`),
  fc.string().map(s => `<div onclick="${s}">test</div>`),
)

// ─── Property Tests ──────────────────────────────────────────────────────────

describe('Property 11: DOMPurify SVG 净化安全性', () => {
  it('sanitized output never contains dangerous tags or attributes', () => {
    fc.assert(
      fc.property(maliciousInputArb, (input) => {
        const result = sanitizeIcon(input)
        expect(result).not.toMatch(/<script[\s>]/i)
        expect(result).not.toMatch(/onerror\s*=/i)
        expect(result).not.toMatch(/onclick\s*=/i)
        expect(result).not.toMatch(/onload\s*=/i)
        expect(result).not.toMatch(/onfocus\s*=/i)
        expect(result).not.toMatch(/onstart\s*=/i)
        expect(result).not.toMatch(/javascript:/i)
        expect(result).not.toMatch(/<iframe[\s>]/i)
        expect(result).not.toMatch(/<style[\s>]/i)
      }),
      { numRuns: 100 },
    )
  })

  it('valid SVG tags are preserved after sanitization', () => {
    const validSvgs = [
      '<svg viewBox="0 0 24 24"><path d="M12 2L2 22h20z"/></svg>',
      '<svg><circle cx="50" cy="50" r="40" fill="red"/></svg>',
      '<svg><rect x="10" y="10" width="80" height="80"/></svg>',
      '<svg><line x="0" y="0" stroke="black"/></svg>',
      '<svg><g transform="translate(10,10)"><path d="M0 0"/></g></svg>',
    ]

    for (const svg of validSvgs) {
      const result = sanitizeIcon(svg)
      expect(result).toContain('<svg')
      // At least one inner SVG element should be preserved
      expect(result.length).toBeGreaterThan('<svg></svg>'.length)
    }
  })

  it('random strings produce safe output', () => {
    fc.assert(
      fc.property(fc.string({ minLength: 0, maxLength: 200 }), (input) => {
        const result = sanitizeIcon(input)
        expect(result).not.toMatch(/<script[\s>]/i)
        expect(result).not.toMatch(/javascript:/i)
      }),
      { numRuns: 100 },
    )
  })
})
