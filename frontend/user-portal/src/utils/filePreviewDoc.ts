import { TEXT_CHAR_LIMIT } from './filePreviewText'

function isAsciiPrintable(code: number): boolean {
  return code === 9 || code === 10 || code === 13 || (code >= 32 && code <= 126)
}

function collectAsciiRuns(bytes: Uint8Array): string[] {
  const runs: string[] = []
  let start = -1
  for (let i = 0; i <= bytes.length; i++) {
    const ok = i < bytes.length && isAsciiPrintable(bytes[i])
    if (ok && start < 0) start = i
    if (!ok && start >= 0) {
      if (i - start >= 8) {
        runs.push(new TextDecoder('latin1').decode(bytes.subarray(start, i)))
      }
      start = -1
    }
  }
  return runs
}

function collectUtf16LeRuns(bytes: Uint8Array): string[] {
  const runs: string[] = []
  let buf = ''
  for (let i = 0; i + 1 < bytes.length; i += 2) {
    const code = bytes[i] | (bytes[i + 1] << 8)
    const ok = code === 9 || code === 10 || code === 13 || (code >= 32 && code < 0xD800)
    if (ok) buf += String.fromCharCode(code)
    else {
      if (buf.trim().length >= 4) runs.push(buf)
      buf = ''
    }
  }
  if (buf.trim().length >= 4) runs.push(buf)
  return runs
}

/** Best-effort readable strings from Word 97–2003 OLE; no layout. */
export function extractDocPreviewText(buffer: ArrayBuffer): { text: string; truncated: boolean } {
  const bytes = new Uint8Array(buffer)
  const parts = [...collectUtf16LeRuns(bytes), ...collectAsciiRuns(bytes)]
  const text = parts.join('\n').replace(/\0/g, '').trim()
  if (text.length <= TEXT_CHAR_LIMIT) return { text, truncated: false }
  return { text: text.slice(0, TEXT_CHAR_LIMIT), truncated: true }
}
