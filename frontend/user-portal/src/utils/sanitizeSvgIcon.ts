import DOMPurify from 'dompurify'

const SVG_PURIFY_CONFIG = {
  ALLOWED_TAGS: ['svg', 'path', 'circle', 'rect', 'line', 'polyline', 'polygon', 'g', 'defs', 'use'],
  ALLOWED_ATTR: ['viewBox', 'd', 'fill', 'stroke', 'stroke-width', 'cx', 'cy', 'r', 'x', 'y',
    'width', 'height', 'points', 'transform', 'class', 'xmlns', 'xlink:href'],
}

export function sanitizeSvgIcon(icon: string): string {
  if (!icon) return ''
  return DOMPurify.sanitize(icon, SVG_PURIFY_CONFIG)
}
