import { eventSequence } from '@platform-shared/formEvents/async'
/**
 * Component-level form-create events (Form Preview + portal parity).
 */

import { walkFormCreateRules } from '@/utils/formDesigner'
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

/** Portal / Preview key for placed SubTable widgets (parity with formRendererRuleParsing). */
export function subTableComponentEventFieldKey(bindingId: number | string): string {
  return `__subTable_${bindingId}`
}

/**
 * Event map key for a form-create rule.
 * SubTable has no `field` — index by `__subTable_${_bindingId}` so Preview/Portal can look up handlers.
 */
export function resolveComponentEventFieldKey(rule: Record<string, unknown>): string {
  const field = rule.field != null ? String(rule.field) : ''
  if (field) return field
  if (String(rule.type ?? '') !== 'subTable') return ''
  const props = (rule.props && typeof rule.props === 'object')
    ? rule.props as Record<string, unknown>
    : {}
  const bindingId = rule._bindingId ?? props._bindingId
  if (bindingId == null || bindingId === '') return ''
  return subTableComponentEventFieldKey(bindingId)
}

function normalizeHandler(raw: unknown): unknown {
  return raw
}

function pickMergedHandler(primary: unknown, secondary: unknown): unknown {
  const p = normalizeHandler(primary)
  const s = normalizeHandler(secondary)
  // Native form-create replaces functions with injection closures and drops their
  // source tags. The designer shadow retains the script for the shared dispatcher.
  if (typeof p === 'function' && (typeof s === 'string' || Array.isArray(s)) && !isEmptyFormCreateHandler(s)) return s
  if (p == null && s == null) return undefined
  if (p != null && !isEmptyFormCreateHandler(p)) return p
  if (s != null && !isEmptyFormCreateHandler(s)) return s
  return p ?? s
}

function mergeRuleOnHandlers(rule: Record<string, unknown>): Record<string, unknown> {
  const a = rule.on && typeof rule.on === 'object' ? (rule.on as Record<string, unknown>) : {}
  const b = rule._on && typeof rule._on === 'object' ? (rule._on as Record<string, unknown>) : {}
  const keys = new Set([...Object.keys(a), ...Object.keys(b)])
  const out: Record<string, unknown> = {}
  for (const k of keys) {
    const picked = pickMergedHandler(a[k], b[k])
    if (picked != null) out[k] = picked
  }
  return out
}

function mergeRuleHookHandlers(rule: Record<string, unknown>): Record<string, unknown> {
  const a = rule.hook && typeof rule.hook === 'object' ? (rule.hook as Record<string, unknown>) : {}
  const b = rule._hook && typeof rule._hook === 'object' ? (rule._hook as Record<string, unknown>) : {}
  const keys = new Set([...Object.keys(a), ...Object.keys(b)])
  const out: Record<string, unknown> = {}
  for (const k of keys) {
    const picked = pickMergedHandler(a[k], b[k])
    if (picked != null) out[k] = picked
  }
  return out
}

function walkRulesCollect(
  items: unknown[],
  map: Map<string, FieldComponentEvents>,
): void {
  walkFormCreateRules(items, (rule) => {
    const field = resolveComponentEventFieldKey(rule)
    if (!field) return
    const on = mergeRuleOnHandlers(rule)
    const hook = mergeRuleHookHandlers(rule)
    map.set(field, { rule, on, hook })
  })
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
  fieldType?: string
}

const RULE_TYPES_MIRROR_BLUR_ON_CHANGE = new Set([
  'select',
  'radio',
  'checkbox',
  'cascader',
  'switch',
  'rate',
  'slider',
  'datePicker',
  'timePicker',
  'dateRange',
  'timeRange',
  'elTreeSelect',
  'treeSelect',
  'treeselect',
  'date',
  'datetime',
  'time',
  'inputNumber',
  'number',
  'user',
  'transfer',
  'colorPicker',
  'lookup',
])

export function shouldMirrorBlurOnChange(
  rule: Record<string, unknown> | undefined,
  fieldType?: string,
): boolean {
  const fromRule = String(rule?.type ?? '')
  const fromField = String(fieldType ?? '')
  return RULE_TYPES_MIRROR_BLUR_ON_CHANGE.has(fromRule)
    || RULE_TYPES_MIRROR_BLUR_ON_CHANGE.has(fromField)
}

function runHandler(
  raw: unknown,
  ctx: {
    field: string
    value: unknown
    api: PortalFormApi
    rule: Record<string, unknown>
  },
): unknown {
  const handler = normalizeHandler(raw)
  if (handler == null || isEmptyFormCreateHandler(handler)) return
  const fn = parseFormCreateEventHandler(handler)
  if (!fn) throw new Error("Invalid form event script")
  return fn(ctx)
}

export function runComponentFieldEvents(
  events: FieldComponentEvents | undefined,
  options: RunComponentEventsOptions,
): unknown {
  if (!events) return
  const ctx = {
    field: options.field,
    value: options.value,
    api: options.api,
    rule: events.rule,
  }
  return eventSequence([
    () => options.onEvent ? runHandler(events.on[options.onEvent], ctx) : undefined,
    () => options.hookEvent ? runHandler(events.hook[options.hookEvent], ctx) : undefined,
  ])
}

/** Run change/hook_value and, for select-like fields, also `on.blur` (designer often uses blur). */
export function runComponentFieldEventsOnValueChange(
  events: FieldComponentEvents | undefined,
  options: RunComponentEventsOptions,
): unknown {
  return eventSequence([
    () => runComponentFieldEvents(events, options),
    () => (options.onEvent || options.hookEvent) && shouldMirrorBlurOnChange(events?.rule, options.fieldType)
      ? runComponentFieldEvents(events, { field: options.field, value: options.value, api: options.api, onEvent: 'blur' }) : undefined,
  ])
}

export { createPortalFormApi }
