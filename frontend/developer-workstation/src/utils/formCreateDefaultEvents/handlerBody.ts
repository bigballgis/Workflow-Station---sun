/**
 * Handler-body primitives: empty handler builders, body extraction, and editor normalization.
 * Form-level: FnConfig expects [[FORM-CREATE-PREFIX-function onSubmit(formData, api){…}-FORM-CREATE-SUFFIX]].
 * Component-level: EventConfig body mode stores $FNX: + function body only.
 */

import {
  FC_COMPONENT_EVENT_PREFIX,
  FC_FN_PREFIX,
  FC_FN_SUFFIX,
  FC_WRAPPER_RE,
} from './constants'

/** Form-level empty handler — must match FnConfig default template (named function). */
export function emptyFormLevelEventFunction(eventName: string, params: string, body = ''): string {
  const inner = body ? `\n  ${body}\n` : ''
  return `${FC_FN_PREFIX}function ${eventName}(${params}){${inner}}${FC_FN_SUFFIX}`
}

/** Component event empty handler — EventConfig body mode ($FNX: prefix). */
export function emptyComponentEventFunction(body = ''): string {
  if (!body) return FC_COMPONENT_EVENT_PREFIX
  const normalized = body.startsWith('\n') ? body : `\n${body}\n`
  return `${FC_COMPONENT_EVENT_PREFIX}${normalized}`
}

export function extractFormCreateHandlerBody(raw: string): string {
  const trimmed = raw.trim()
  if (!trimmed) return ''
  if (trimmed.startsWith(FC_COMPONENT_EVENT_PREFIX)) {
    return trimmed.slice(FC_COMPONENT_EVENT_PREFIX.length).replace(/^\n+|\n+$/g, '')
  }
  const fnKeyword = trimmed.indexOf('function')
  const openIdx = fnKeyword >= 0 ? trimmed.indexOf('{', fnKeyword) : trimmed.indexOf('{')
  if (openIdx < 0) return ''
  let depth = 0
  for (let i = openIdx; i < trimmed.length; i++) {
    const ch = trimmed[i]
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) {
        return trimmed.slice(openIdx + 1, i).replace(/^\n+|\n+$/g, '')
      }
    }
  }
  return ''
}

/** EventConfig body editor: strip `$FNX:`, FORM-CREATE wrapper, or pasted `function name(...) { }`. */
export function normalizeEventEditorBody(raw: string): string {
  const trimmed = (raw || '').trim()
  if (!trimmed) return ''
  let body = ''
  if (trimmed.startsWith(FC_COMPONENT_EVENT_PREFIX)) {
    body = trimmed.slice(FC_COMPONENT_EVENT_PREFIX.length).replace(/^\n+|\n+$/g, '')
  } else if (trimmed.startsWith(FC_FN_PREFIX) || /^\s*function\s+/i.test(trimmed)) {
    body = extractFormCreateHandlerBody(trimmed)
  } else {
    body = trimmed.replace(/^\n+|\n+$/g, '')
  }
  return body.trim()
}

export function isEmptyFormCreateHandler(raw: unknown): boolean {
  if (raw == null || raw === '') return true
  if (typeof raw !== 'string') return false
  const trimmed = raw.trim()
  if (!trimmed) return true
  if (trimmed === FC_COMPONENT_EVENT_PREFIX) return true
  return extractFormCreateHandlerBody(trimmed) === ''
}

export function normalizeHandlerValue(raw: unknown): unknown {
  if (Array.isArray(raw)) return raw[0]
  return raw
}

export function pickMergedHandler(primary: unknown, secondary: unknown): unknown {
  const p = normalizeHandlerValue(primary)
  const s = normalizeHandlerValue(secondary)
  if (!isEmptyFormCreateHandler(p)) return p
  if (!isEmptyFormCreateHandler(s)) return s
  return p ?? s
}

export function normalizeComponentEventHandler(raw: unknown): string {
  if (raw == null || raw === '') return emptyComponentEventFunction()
  if (typeof raw !== 'string') return emptyComponentEventFunction()
  const trimmed = raw.trim()
  if (!trimmed || isEmptyFormCreateHandler(trimmed)) return emptyComponentEventFunction()
  if (trimmed.startsWith(FC_COMPONENT_EVENT_PREFIX)) return trimmed
  if (trimmed.match(FC_WRAPPER_RE)) {
    const body = extractFormCreateHandlerBody(trimmed)
    return emptyComponentEventFunction(body)
  }
  return emptyComponentEventFunction(trimmed)
}
