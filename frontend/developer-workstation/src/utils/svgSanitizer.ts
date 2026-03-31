import DOMPurify from 'dompurify'

const SVG_PURIFY_CONFIG = {
  USE_PROFILES: { svg: true, svgFilters: true },
  ADD_TAGS: ['use'],
  ADD_ATTR: [
    'xlink:href', 'fill', 'stroke', 'viewBox', 'xmlns', 'xmlns:xlink',
    'd', 'points', 'cx', 'cy', 'r', 'rx', 'ry', 'x', 'y',
    'x1', 'y1', 'x2', 'y2', 'width', 'height', 'transform', 'opacity',
    'fill-opacity', 'stroke-width', 'stroke-linecap', 'stroke-linejoin',
    'stroke-dasharray', 'stroke-dashoffset', 'fill-rule', 'clip-rule',
    'clip-path', 'mask', 'filter', 'color-interpolation-filters',
    'flood-color', 'flood-opacity', 'lighting-color',
  ],
  FORBID_TAGS: ['script', 'style'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover'],
}

export function sanitizeSvg(svgContent: string): string {
  if (!svgContent) return ''
  return DOMPurify.sanitize(svgContent, SVG_PURIFY_CONFIG)
}
