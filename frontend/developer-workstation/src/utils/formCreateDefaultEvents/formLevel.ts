/**
 * Form-level (options) event handling: normalize persisted handlers, seed defaults,
 * and serialize designer getOption() output as fc-designer FnConfig strings.
 */

import { FC_WRAPPER_RE, FORM_LEVEL_EVENT_DEFS } from './constants'
import {
  emptyFormLevelEventFunction,
  extractFormCreateHandlerBody,
  isEmptyFormCreateHandler,
  normalizeHandlerValue,
} from './handlerBody'

/**
 * Normalize persisted form-level handlers so FnEditor can extract the body (requires `{` `}`).
 * Fixes legacy anonymous `function (params){}` wrappers produced by earlier seeds.
 */
export function normalizeFormLevelEventHandler(
  eventName: string,
  params: string,
  raw: unknown,
): string {
  if (raw == null || raw === '' || typeof raw !== 'string') {
    return emptyFormLevelEventFunction(eventName, params)
  }
  const trimmed = raw.trim()
  if (!trimmed || isEmptyFormCreateHandler(trimmed)) {
    return emptyFormLevelEventFunction(eventName, params)
  }

  const body = extractFormCreateHandlerBody(trimmed)
  const wrapped = trimmed.match(FC_WRAPPER_RE)
  if (wrapped) {
    const inner = wrapped[1].trim()
    const namedPattern = new RegExp(`^function\\s+${eventName}\\s*\\(`)
    if (namedPattern.test(inner) && inner.includes('{') && inner.includes('}')) {
      return trimmed
    }
  }

  return emptyFormLevelEventFunction(eventName, params, body)
}

/** Merge default empty form-level events into options (non-destructive). */
export function ensureEmptyFormOptionsEvents(
  options: Record<string, unknown> | undefined,
): Record<string, unknown> {
  const next: Record<string, unknown> = { ...(options || {}) }
  for (const { name, params } of FORM_LEVEL_EVENT_DEFS) {
    const current = normalizeHandlerValue(next[name])
    next[name] = normalizeFormLevelEventHandler(name, params, current)
  }
  return next
}

export function buildDefaultFormCreateOptions(
  base: Record<string, unknown> = {},
): Record<string, unknown> {
  return ensureEmptyFormOptionsEvents(base)
}

/**
 * Persist form-level handlers as fc-designer FnConfig strings.
 * Designer runtime may hold {@link wrapFormLevelOnChangeForFormCreate} functions — JSON drops them.
 */
export function serializeFormLevelEventHandlerForPersist(
  eventName: string,
  params: string,
  raw: unknown,
): string {
  if (typeof raw === 'function') {
    const source = (raw as { __hermesFormEventSource?: unknown }).__hermesFormEventSource
    if (source != null) {
      return normalizeFormLevelEventHandler(eventName, params, source)
    }
    const body = extractFormCreateHandlerBody(String(raw))
    return emptyFormLevelEventFunction(eventName, params, body)
  }
  return normalizeFormLevelEventHandler(eventName, params, raw)
}

/** Normalize options from designer getOption() before writing configJson. */
export function serializeFormCreateOptionsForPersist(
  options: Record<string, unknown> | undefined,
): Record<string, unknown> {
  if (!options || typeof options !== 'object') return {}
  const next: Record<string, unknown> = { ...options }
  for (const { name, params } of FORM_LEVEL_EVENT_DEFS) {
    if (name in next) {
      next[name] = serializeFormLevelEventHandlerForPersist(name, params, next[name])
    }
  }
  return next
}
