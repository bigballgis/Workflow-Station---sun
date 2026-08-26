export const TEXT_CHAR_LIMIT = 500_000

export function decodeTextPreview(buffer: ArrayBuffer): { text: string; truncated: boolean } {
  const decoded = new TextDecoder('utf-8', { fatal: false }).decode(buffer)
  if (decoded.length <= TEXT_CHAR_LIMIT) return { text: decoded, truncated: false }
  return { text: decoded.slice(0, TEXT_CHAR_LIMIT), truncated: true }
}
