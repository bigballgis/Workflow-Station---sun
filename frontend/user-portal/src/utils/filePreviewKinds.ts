export type FilePreviewKind =
  | 'image'
  | 'pdf'
  | 'text'
  | 'spreadsheet'
  | 'docx'
  | 'doc'
  | 'tiff'
  | 'pptx'
  | 'unsupported'

const NEVER_PREVIEW = new Set([
  'ppt', 'zip', 'rar', '7z', 'msg', 'eml', 'gz', 'tgz', 'tar', 'exe', 'dll',
])

const IMAGE_EXT = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'])
const TEXT_EXT = new Set([
  'txt', 'log', 'md', 'markdown', 'json', 'xml', 'yml', 'yaml',
  'html', 'htm', 'css', 'js', 'ts', 'tsx', 'jsx', 'svg', 'sql',
  'sh', 'bat', 'ini', 'conf', 'properties', 'rtf', 'csv',
])

export function fileExtension(name: string): string {
  const cleaned = String(name || '').split('?')[0].split('#')[0]
  const base = cleaned.replace(/\\/g, '/').split('/').pop() || cleaned
  const dot = base.lastIndexOf('.')
  if (dot <= 0 || dot === base.length - 1) return ''
  return base.slice(dot + 1).toLowerCase()
}

export function isBlockedPreviewExtension(ext: string): boolean {
  return NEVER_PREVIEW.has(ext)
}

export function kindFromExtension(ext: string): FilePreviewKind {
  if (!ext || NEVER_PREVIEW.has(ext)) return 'unsupported'
  if (IMAGE_EXT.has(ext)) return 'image'
  if (ext === 'pdf') return 'pdf'
  if (ext === 'tif' || ext === 'tiff') return 'tiff'
  if (TEXT_EXT.has(ext)) return 'text'
  if (ext === 'docx') return 'docx'
  if (ext === 'doc') return 'doc'
  if (ext === 'xlsx' || ext === 'xls') return 'spreadsheet'
  if (ext === 'pptx') return 'pptx'
  return 'unsupported'
}

export function kindFromMime(mime: string): FilePreviewKind | null {
  const type = mime.split(';')[0].trim().toLowerCase()
  if (!type) return null
  if (type === 'image/svg+xml' || type.startsWith('text/')) return 'text'
  if (type === 'image/tiff' || type === 'image/tif') return 'tiff'
  if (type.startsWith('image/')) return 'image'
  if (type === 'application/pdf') return 'pdf'
  return null
}
