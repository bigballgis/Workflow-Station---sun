import type DOMPurifyType from 'dompurify'
let _domPurify: DOMPurifyType | null = null

/** Sanitize HTML content to prevent XSS — lazy-loads DOMPurify on first call */
async function getDomPurify(): Promise<DOMPurifyType> {
  if (!_domPurify) {
    const mod = await import('dompurify')
    _domPurify = mod.default
  }
  return _domPurify
}

export function sanitizeHtml(html: string): string {
  // Synchronous fallback: strip all tags if DOMPurify not yet loaded
  if (!_domPurify) {
    // Trigger lazy load (fire-and-forget, next render will use cached instance)
    getDomPurify()
    return html.replace(/<[^>]*>/g, '')
  }
  return _domPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 's', 'ol', 'ul', 'li',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a', 'img', 'table', 'tr', 'td', 'th', 'span', 'div'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'style', 'target', 'rel'],
  })
}
