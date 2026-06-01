/**
 * Portal runtime — execute per-component form-create events (rule.on / rule._hook).
 */

import { getRuleChildren } from '@/components/formRendererHelpers'
import {
  createPortalFormApi,
  isEmptyFormCreateHandler,
  parseFormCreateEventHandler,
  type FieldKeyResolver,
  type PortalFormApi,
  type PortalFormVisibilityState,
} from '@/utils/formCreateEventRuntime'

export interface FieldComponentEvents {
  rule: Record<string, unknown>
  on: Record<string, unknown>
  hook: Record<string, unknown>
}

function normalizeHandler(raw: unknown): unknown {
  if (Array.isArray(raw)) return raw[0]
  return raw
}

function pickMergedHandler(primary: unknown, secondary: unknown): unknown {
  const p = normalizeHandler(primary)
  const s = normalizeHandler(secondary)
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
  if (!Array.isArray(items)) return
  for (const raw of items) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    const field = rule.field != null ? String(rule.field) : ''
    if (field) {
      const on = mergeRuleOnHandlers(rule)
      const hook = mergeRuleHookHandlers(rule)
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
  /** DOM-style event on rule.on, e.g. change / blur */
  onEvent?: string
  /** hook_* name without prefix, e.g. value / load */
  hookEvent?: string
  /** Portal `FormField.type` when rule.type alone is insufficient */
  fieldType?: string
}

/** Select-like controls: portal does not fire DOM blur reliably; mirror `on.blur` on change. */
/**
 * Discrete-value controls: designer often uses on.blur; DOM blur is missing or unreliable.
 * Do NOT add text/input/textarea/password — they have real blur; mirroring would run blur every keystroke.
 */
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

/** Run change/hook_value and, for select-like fields, also `on.blur` (designer often uses blur). */
export function runComponentFieldEventsOnValueChange(
  events: FieldComponentEvents | undefined,
  options: RunComponentEventsOptions,
): void {
  runComponentFieldEvents(events, options)
  if (!options.onEvent && !options.hookEvent) return
  if (!shouldMirrorBlurOnChange(events?.rule, options.fieldType)) return
  runComponentFieldEvents(events, {
    field: options.field,
    value: options.value,
    api: options.api,
    onEvent: 'blur',
  })
}

export function runAllComponentHookEvents(
  rules: unknown[] | undefined | null,
  hookName: string,
  getFormData: () => Record<string, unknown>,
  applyPatch: (patch: Record<string, unknown>) => void,
  resolveFieldKey?: FieldKeyResolver,
  visibility?: {
    state: PortalFormVisibilityState
    notify: () => void
    getAllFieldKeys: () => string[]
  },
): void {
  const map = collectFieldComponentEventsFromRules(rules)
  const api = createPortalFormApi(getFormData, applyPatch, resolveFieldKey, visibility)
  for (const [field, ev] of map) {
    runComponentFieldEvents(ev, {
      field,
      value: getFormData()[field],
      api,
      hookEvent: hookName,
    })
  }
}
