/**
 * form-create designer script primitives: detect/normalize/extract the stored
 * function strings ([[FORM-CREATE-PREFIX-...]] / $FNX:) and guard dangerous source.
 */

const FC_FN_WRAPPER_RE =
  /^\[\[FORM-CREATE-PREFIX-function\s([\s\S]*)\}-FORM-CREATE-SUFFIX\]\]$/
const FC_FNX_PREFIX = '$FNX:'

const DANGEROUS_KEYWORDS = /\b(eval|Function|import|require|window|document|globalThis|process)\b/

export function containsDangerousFormScript(source: string): boolean {
  return DANGEROUS_KEYWORDS.test(source)
}

export function extractFunctionBody(source: string): string | null {
  const openIdx = source.indexOf('{')
  if (openIdx < 0) return null
  let depth = 0
  for (let i = openIdx; i < source.length; i++) {
    const ch = source[i]
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) {
        return source.slice(openIdx + 1, i).trim()
      }
    }
  }
  return null
}

export function normalizeFunctionSource(raw: string): string | null {
  const trimmed = raw.trim()
  if (!trimmed) return null

  if (trimmed.startsWith(FC_FNX_PREFIX)) {
    const body = trimmed.slice(FC_FNX_PREFIX.length).trim()
    return `function($inject) {\n${body}\n}`
  }

  const wrapped = trimmed.match(FC_FN_WRAPPER_RE)
  if (wrapped) {
    return `function ${wrapped[1]}}`
  }
  if (trimmed.startsWith('function')) {
    return trimmed
  }
  return null
}

export function isEmptyFormCreateHandler(raw: unknown): boolean {
  if (raw == null || raw === '') return true
  if (typeof raw !== 'string') return false
  const trimmed = raw.trim()
  if (!trimmed) return true
  if (trimmed === FC_FNX_PREFIX) return true
  if (trimmed.startsWith(FC_FNX_PREFIX)) {
    return trimmed.slice(FC_FNX_PREFIX.length).trim() === ''
  }
  const openIdx = trimmed.indexOf('{')
  if (openIdx < 0) return true
  let depth = 0
  for (let i = openIdx; i < trimmed.length; i++) {
    const ch = trimmed[i]
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) {
        return trimmed.slice(openIdx + 1, i).trim() === ''
      }
    }
  }
  return true
}
