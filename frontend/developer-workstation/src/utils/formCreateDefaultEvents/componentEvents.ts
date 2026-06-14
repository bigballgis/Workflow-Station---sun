/**
 * Component-level (rule on/hook) event handling: merge designer `_on`/`_hook` buckets,
 * flatten/serialize for persist, inflate for designer, and seed default empty handlers.
 */

import { getRuleChildren } from '@/utils/formDesigner'

import { COMPONENT_HOOK_NAMES, FC_COMPONENT_EVENT_PREFIX, LAYOUT_TYPES, TYPE_ON_EVENTS } from './constants'
import {
  emptyComponentEventFunction,
  extractFormCreateHandlerBody,
  isEmptyFormCreateHandler,
  normalizeComponentEventHandler,
  normalizeHandlerValue,
  pickMergedHandler,
} from './handlerBody'

/** Runtime + persist: merge `on` with designer `_on` (non-empty wins). */
export function mergeRuleOnHandlers(rule: Record<string, unknown>): Record<string, unknown> {
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

/** Runtime + persist: merge `hook` with designer `_hook`. */
export function mergeRuleHookHandlers(rule: Record<string, unknown>): Record<string, unknown> {
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

/**
 * Before persisting config_json: copy `_on` / `_hook` into `on` / `hook` so User Portal reads scripts.
 * form-create getRule() may omit designer-only `_on` buckets.
 */
export function flattenComponentEventsForPersist(rules: unknown[]): void {
  if (!Array.isArray(rules)) return
  for (const raw of rules) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    const on = mergeRuleOnHandlers(rule)
    const hook = mergeRuleHookHandlers(rule)
    if (Object.keys(on).length > 0) rule.on = on
    else delete rule.on
    delete rule._on
    if (Object.keys(hook).length > 0) rule.hook = hook
    else delete rule.hook
    delete rule._hook
    const children = getRuleChildren(rule)
    if (children.length) flattenComponentEventsForPersist(children)
  }
}

/**
 * Persist component handlers as EventConfig `$FNX:` strings.
 * Designer / preview runtime may hold function handlers — JSON drops them.
 */
export function serializeComponentEventHandlerForPersist(raw: unknown): string {
  if (typeof raw === 'function') {
    const source = (raw as { __hermesFormEventSource?: unknown }).__hermesFormEventSource
    if (source != null) {
      return normalizeComponentEventHandler(source)
    }
    const body = extractFormCreateHandlerBody(String(raw))
    return emptyComponentEventFunction(body)
  }
  return normalizeComponentEventHandler(raw)
}

function serializeComponentHandlerBucket(bucket: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(bucket)) {
    const serialized = serializeComponentEventHandlerForPersist(value)
    if (!isEmptyFormCreateHandler(serialized)) {
      out[key] = serialized
    }
  }
  return out
}

function serializeComponentEventsOnRule(rule: Record<string, unknown>): void {
  for (const key of ['on', 'hook'] as const) {
    const bucket = rule[key]
    if (!bucket || typeof bucket !== 'object') continue
    const out = serializeComponentHandlerBucket(bucket as Record<string, unknown>)
    if (Object.keys(out).length > 0) {
      rule[key] = out
    } else {
      delete rule[key]
    }
  }
  delete rule._on
  delete rule._hook
}

/** Walk rule tree; convert on/hook function handlers to `$FNX:` strings (call after flatten). */
export function serializeComponentEventsForPersist(rules: unknown[]): void {
  if (!Array.isArray(rules)) return
  for (const raw of rules) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    serializeComponentEventsOnRule(rule)
    const children = getRuleChildren(rule)
    if (children.length) serializeComponentEventsForPersist(children)
  }
}

/** Flatten designer `_on` / `_hook` and serialize handlers before writing configJson. */
export function prepareFormCreateRulesForPersist(rules: unknown[]): void {
  flattenComponentEventsForPersist(rules)
  serializeComponentEventsForPersist(rules)
}

/**
 * After loading config_json into fc-designer: copy persisted `on` / `hook` into `_on` / `_hook`
 * so Hermes EventConfig reads scripts (inverse of {@link flattenComponentEventsForPersist}).
 */
export function inflateComponentEventsForDesigner(rules: unknown[]): void {
  if (!Array.isArray(rules)) return
  for (const raw of rules) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    const on = mergeRuleOnHandlers(rule)
    if (Object.keys(on).length > 0) {
      rule._on = on
    }
    const hook = mergeRuleHookHandlers(rule)
    if (Object.keys(hook).length > 0) {
      rule._hook = hook
    }
    const children = getRuleChildren(rule)
    if (children.length) inflateComponentEventsForDesigner(children)
  }
}

/** True when rule is a live fc-designer canvas node (uses `_on` / `_hook`). */
export function isDesignerCanvasRule(rule: Record<string, unknown>): boolean {
  return rule._fc_id != null || rule._menu != null
}

/** Designer EventConfig reads `_on`; getRule/parseRule exports `on`. */
function resolveRuleOnStore(rule: Record<string, unknown>): {
  key: '_on' | 'on'
  bucket: Record<string, unknown>
} {
  if (isDesignerCanvasRule(rule)) {
    const bucket = (rule._on && typeof rule._on === 'object'
      ? { ...(rule._on as Record<string, unknown>) }
      : {}) as Record<string, unknown>
    if (rule.on && typeof rule.on === 'object' && Object.keys(bucket).length === 0) {
      Object.assign(bucket, rule.on as Record<string, unknown>)
    }
    return { key: '_on', bucket }
  }
  const bucket = (rule.on && typeof rule.on === 'object'
    ? { ...(rule.on as Record<string, unknown>) }
    : {}) as Record<string, unknown>
  return { key: 'on', bucket }
}

function resolveRuleHookStore(rule: Record<string, unknown>): {
  key: '_hook' | 'hook'
  bucket: Record<string, unknown>
} {
  if (isDesignerCanvasRule(rule)) {
    const bucket = (rule._hook && typeof rule._hook === 'object'
      ? { ...(rule._hook as Record<string, unknown>) }
      : {}) as Record<string, unknown>
    if (rule.hook && typeof rule.hook === 'object' && Object.keys(bucket).length === 0) {
      Object.assign(bucket, rule.hook as Record<string, unknown>)
    }
    return { key: '_hook', bucket }
  }
  const bucket = (rule.hook && typeof rule.hook === 'object'
    ? { ...(rule.hook as Record<string, unknown>) }
    : rule._hook && typeof rule._hook === 'object'
      ? { ...(rule._hook as Record<string, unknown>) }
      : {}) as Record<string, unknown>
  return { key: 'hook', bucket }
}

export function getComponentEventNamesForRule(rule: Record<string, unknown>): {
  on: string[]
  hooks: string[]
} {
  const type = String(rule.type ?? '')
  const hooks = [...COMPONENT_HOOK_NAMES]
  if (LAYOUT_TYPES.has(type) || (!rule.field && type !== 'subTable' && type !== 'linkForm')) {
    return { on: ['click'], hooks }
  }
  const on = TYPE_ON_EVENTS[type] ?? ['change']
  return { on: [...new Set([...on, 'blur', 'focus'])], hooks }
}

function ensureOnEvents(rule: Record<string, unknown>, eventNames: string[]): boolean {
  let changed = false
  const { key, bucket } = resolveRuleOnStore(rule)
  const on = bucket
  for (const name of eventNames) {
    const current = normalizeHandlerValue(on[name])
    if (current == null || current === '' || isEmptyFormCreateHandler(current)) {
      if (on[name] !== emptyComponentEventFunction()) {
        on[name] = emptyComponentEventFunction()
        changed = true
      }
    } else if (typeof current === 'string' && !current.startsWith(FC_COMPONENT_EVENT_PREFIX)) {
      on[name] = normalizeComponentEventHandler(current)
      changed = true
    }
  }
  if (changed || rule[key] == null) {
    rule[key] = on
    if (key === '_on' && rule.on) delete rule.on
  }
  return changed
}

function ensureHookEvents(rule: Record<string, unknown>, hookNames: string[]): boolean {
  let changed = false
  const { key, bucket } = resolveRuleHookStore(rule)
  const hook = bucket
  for (const name of hookNames) {
    const current = normalizeHandlerValue(hook[name])
    if (current == null || current === '' || isEmptyFormCreateHandler(current)) {
      if (hook[name] !== emptyComponentEventFunction()) {
        hook[name] = emptyComponentEventFunction()
        changed = true
      }
    } else if (typeof current === 'string' && !current.startsWith(FC_COMPONENT_EVENT_PREFIX)) {
      hook[name] = normalizeComponentEventHandler(current)
      changed = true
    }
  }
  if (changed || rule[key] == null) {
    rule[key] = hook
    if (key === '_hook' && rule.hook) delete rule.hook
  }
  return changed
}

/** Seed default empty handlers on a single rule (mutates). Returns true if modified. */
export function ensureEmptyRuleComponentEvents(rule: Record<string, unknown>): boolean {
  const { on, hooks } = getComponentEventNamesForRule(rule)
  let changed = false
  if (ensureOnEvents(rule, on)) changed = true
  if (ensureHookEvents(rule, hooks)) changed = true
  const children = getRuleChildren(rule)
  for (const child of children) {
    if (child && typeof child === 'object') {
      if (ensureEmptyRuleComponentEvents(child as Record<string, unknown>)) changed = true
    }
  }
  return changed
}

/** Walk rule tree; returns true if any rule was updated. */
export function walkRulesEnsureComponentEvents(rules: unknown[]): boolean {
  if (!Array.isArray(rules)) return false
  let changed = false
  for (const raw of rules) {
    if (!raw || typeof raw !== 'object') continue
    if (ensureEmptyRuleComponentEvents(raw as Record<string, unknown>)) changed = true
  }
  return changed
}

/** fc-designer `config.updateDefaultRule` — seed events when dragging components in. */
export function buildDesignerUpdateDefaultRule(): Record<string, (rule: Record<string, unknown>) => void> {
  const handler = (rule: Record<string, unknown>) => {
    ensureEmptyRuleComponentEvents(rule)
  }
  const names = new Set<string>([
    'default',
    ...Object.keys(TYPE_ON_EVENTS),
    ...LAYOUT_TYPES,
  ])
  const map: Record<string, (rule: Record<string, unknown>) => void> = {}
  for (const name of names) {
    map[name] = handler
  }
  return map
}
