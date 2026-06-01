/**
 * Component-level form-create events (Form Preview + portal parity).
 */

import { getRuleChildren } from '@/utils/formDesigner'
import {
  createPortalFormApi,
  isEmptyFormCreateHandler,
  parseFormCreateEventHandler,
  type PortalFormApi,
} from '@/utils/formCreateEventRuntime'

export interface FieldComponentEvents {
  rule: Record<string, unknown>
  on: Record<string, unknown>
  hook: Record<string, unknown>
}

function walkRulesCollect(
  items: unknown[],
  map: Map<string, FieldComponentEvents>,
): void {
  if (!Array.isArray(items)) return
  for (const raw of items) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    const field = rule.field != null ? String(rule.field) : ''
    if (field) {
      const on = (rule.on && typeof rule.on === 'object'
        ? (rule.on as Record<string, unknown>)
        : {}) as Record<string, unknown>
      const hookRaw = rule.hook ?? rule._hook
      const hook = (hookRaw && typeof hookRaw === 'object'
        ? (hookRaw as Record<string, unknown>)
        : {}) as Record<string, unknown>
      map.set(field, { rule, on, hook })
    }
    walkRulesCollect(getRuleChildren(rule), map)
  }
}

export function collectFieldComponentEventsFromRules(
  rules: unknown[] | undefined | null,
): Map<string, FieldComponentEvents> {
  const map = new Map<string, FieldComponentEvents>()
  if (!Array.isArray(rules)) return map
  walkRulesCollect(rules, map)
  return map
}

export interface RunComponentEventsOptions {
  field: string
  value: unknown
  api: PortalFormApi
  onEvent?: string
  hookEvent?: string
}

function normalizeHandler(raw: unknown): unknown {
  if (Array.isArray(raw)) return raw[0]
  return raw
}

function runHandler(
  raw: unknown,
  ctx: {
    field: string
    value: unknown
    api: PortalFormApi
    rule: Record<string, unknown>
  },
): void {
  const handler = normalizeHandler(raw)
  if (handler == null || isEmptyFormCreateHandler(handler)) return
  const fn = parseFormCreateEventHandler(handler)
  if (!fn) return
  try {
    fn(ctx)
  } catch (err) {
    console.warn('[formCreateComponentEvents] handler error:', err)
  }
}

export function runComponentFieldEvents(
  events: FieldComponentEvents | undefined,
  options: RunComponentEventsOptions,
): void {
  if (!events) return
  const ctx = {
    field: options.field,
    value: options.value,
    api: options.api,
    rule: events.rule,
  }
  if (options.onEvent) {
    runHandler(events.on[options.onEvent], ctx)
  }
  if (options.hookEvent) {
    runHandler(events.hook[options.hookEvent], ctx)
  }
}
