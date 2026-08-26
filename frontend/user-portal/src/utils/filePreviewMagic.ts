import type { FilePreviewKind } from './filePreviewKinds'

function hasPrefix(bytes: Uint8Array, sig: number[]): boolean {
  if (bytes.length < sig.length) return false
  return sig.every((b, i) => bytes[i] === b)
}

function asciiContains(bytes: Uint8Array, token: string): boolean {
  const needle = new TextEncoder().encode(token)
  if (needle.length === 0 || bytes.length < needle.length) return false
  outer: for (let i = 0; i <= bytes.length - needle.length; i++) {
    for (let j = 0; j < needle.length; j++) {
      if (bytes[i + j] !== needle[j]) continue outer
    }
    return true
  }
  return false
}

const JPEG = [0xFF, 0xD8, 0xFF]
const PNG = [0x89, 0x50, 0x4E, 0x47]
const GIF = [0x47, 0x49, 0x46, 0x38]
const BMP = [0x42, 0x4D]
const PDF = [0x25, 0x50, 0x44, 0x46]
const PDF_HEADER_SCAN = 1024

/** ISO 32000 allows junk/BOM before %PDF; Acrobat accepts up to 1024 bytes. */
function isPdfBytes(bytes: Uint8Array): boolean {
  let start = 0
  if (bytes.length >= 3 && bytes[0] === 0xEF && bytes[1] === 0xBB && bytes[2] === 0xBF) {
    start = 3
  }
  const limit = Math.min(bytes.length, start + PDF_HEADER_SCAN)
  for (let i = start; i <= limit - PDF.length; i++) {
    if (hasPrefix(bytes.subarray(i), PDF)) return true
  }
  return false
}

const OLE = [0xD0, 0xCF, 0x11, 0xE0]
const TIFF_LE = [0x49, 0x49, 0x2A, 0x00]
const TIFF_BE = [0x4D, 0x4D, 0x00, 0x2A]
const PK = [0x50, 0x4B]

function isWebp(bytes: Uint8Array): boolean {
  return hasPrefix(bytes, [0x52, 0x49, 0x46, 0x46])
    && bytes.length >= 12
    && bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50
}

function isZip(bytes: Uint8Array): boolean {
  return hasPrefix(bytes, PK)
}

function isOle(bytes: Uint8Array): boolean {
  return hasPrefix(bytes, OLE)
}

/** Confirm extension-based kind against magic bytes. Mismatch → unsupported (fail closed). */
export function confirmPreviewKind(
  kind: FilePreviewKind,
  bytes: Uint8Array | undefined,
): FilePreviewKind {
  if (!bytes || bytes.length === 0 || kind === 'unsupported') return kind
  if (kind === 'image') {
    if (hasPrefix(bytes, JPEG) || hasPrefix(bytes, PNG) || hasPrefix(bytes, GIF)
      || hasPrefix(bytes, BMP) || isWebp(bytes)) return 'image'
    return 'unsupported'
  }
  if (kind === 'pdf') return isPdfBytes(bytes) ? 'pdf' : 'unsupported'
  if (kind === 'tiff') {
    return (hasPrefix(bytes, TIFF_LE) || hasPrefix(bytes, TIFF_BE)) ? 'tiff' : 'unsupported'
  }
  if (kind === 'docx') {
    return isZip(bytes) && asciiContains(bytes, 'word/') ? 'docx' : 'unsupported'
  }
  if (kind === 'pptx') {
    return isZip(bytes) && asciiContains(bytes, 'ppt/') ? 'pptx' : 'unsupported'
  }
  if (kind === 'spreadsheet') {
    if (isOle(bytes)) return 'spreadsheet'
    if (isZip(bytes) && asciiContains(bytes, 'xl/')) return 'spreadsheet'
    return 'unsupported'
  }
  if (kind === 'doc') return isOle(bytes) ? 'doc' : 'unsupported'
  if (kind === 'text') return kind
  return kind
}
