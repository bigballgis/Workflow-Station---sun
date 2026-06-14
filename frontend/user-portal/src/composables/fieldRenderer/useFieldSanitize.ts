// ---------------------------------------------------------------------------
// FieldRenderer — XSS sanitization (Task 6.5)
// Behaviour copied verbatim from FieldRenderer.vue.
// ---------------------------------------------------------------------------
import DOMPurify from 'dompurify'

const SAFE_TAGS = [
  'p', 'br', 'strong', 'em', 'u', 's',
  'ol', 'ul', 'li',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'a', 'img',
  'table', 'tr', 'td', 'th',
  'span', 'div',
]

export function useFieldSanitize() {
  function sanitize(html: string): string {
    return DOMPurify.sanitize(html, {
      ALLOWED_TAGS: SAFE_TAGS,
      ALLOWED_ATTR: [
        'href', 'target', 'rel',
        'src', 'alt', 'width', 'height',
        'class', 'style',
        'colspan', 'rowspan',
      ],
    })
  }

  return { sanitize }
}
