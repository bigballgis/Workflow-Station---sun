/**
 * Default empty form-create event handlers (form-level options + per-component on/_hook).
 * Form-level: FnConfig expects [[FORM-CREATE-PREFIX-function onSubmit(formData, api){…}-FORM-CREATE-SUFFIX]].
 * Component-level: EventConfig body mode stores $FNX: + function body only.
 */

import { getRuleChildren } from '@/utils/formDesigner'

export const FC_FN_PREFIX = '[[FORM-CREATE-PREFIX-'
export const FC_FN_SUFFIX = '-FORM-CREATE-SUFFIX]]'
export const FC_COMPONENT_EVENT_PREFIX = '$FNX:'

const FC_WRAPPER_RE =
  /^\[\[FORM-CREATE-PREFIX-function\s([\s\S]*)\}-FORM-CREATE-SUFFIX\]\]$/

/** Form tab → Form event (stored on options). Matches @form-create/designer form.js eventConfig. */
export const FORM_LEVEL_EVENT_DEFS: ReadonlyArray<{ name: string; params: string }> = [
  { name: 'onSubmit', params: 'formData, api' },
  { name: 'onReset', params: 'api' },
  { name: 'onCreated', params: 'api' },
  { name: 'onMounted', params: 'api' },
  { name: 'onReload', params: 'api' },
  { name: 'onChange', params: 'field, value, options' },
  { name: 'beforeSubmit', params: 'formData, data' },
  { name: 'beforeFetch', params: 'config, data' },
] as const

/** Component lifecycle hooks (stored on rule._hook). */
export const COMPONENT_HOOK_NAMES = [
  'load',
  'mounted',
  'deleted',
  'watch',
  'value',
  'hidden',
  'titleClick',
] as const

/** Common DOM events (stored on rule.on). */
export const COMMON_COMPONENT_ON_EVENTS = [
  'change',
  'blur',
  'focus',
  'input',
  'click',
  'clear',
] as const

const LAYOUT_TYPES = new Set([
  'fcRow',
  'col',
  'elCard',
  'elTabs',
  'elTabPane',
  'elCollapse',
  'elCollapseItem',
  'fcTitle',
  'html',
  'div',
  'elDivider',
  'elAlert',
  'space',
])

/** Per-type extra events (form-create designer locale). */
const TYPE_ON_EVENTS: Record<string, string[]> = {
  input: ['change'],
  textarea: ['change'],
  password: ['change'],
  inputNumber: ['change'],
  radio: ['change'],
  checkbox: ['change'],
  select: ['change', 'removeTag', 'visibleChange'],
  switch: ['change'],
  slider: ['change'],
  rate: ['change'],
  datePicker: ['change', 'calendarChange', 'panelChange'],
  timePicker: ['change'],
  dateRange: ['change', 'calendarChange'],
  timeRange: ['change'],
  cascader: ['change', 'expandChange', 'removeTag'],
  upload: ['remove', 'preview', 'error', 'progress', 'exceed'],
  elTreeSelect: ['change', 'removeTag'],
  tree: ['nodeClick', 'checkChange', 'nodeExpand', 'nodeCollapse'],
  elTabs: ['tabClick', 'tabChange', 'tabAdd', 'tabRemove'],
  elTransfer: ['leftCheckChange', 'rightCheckChange'],
  lookup: ['change'],
  subTable: ['change'],
  linkForm: ['change'],
}

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

export function isEmptyFormCreateHandler(raw: unknown): boolean {
  if (raw == null || raw === '') return true
  if (typeof raw !== 'string') return false
  const trimmed = raw.trim()
  if (!trimmed) return true
  if (trimmed === FC_COMPONENT_EVENT_PREFIX) return true
  return extractFormCreateHandlerBody(trimmed) === ''
}

function normalizeHandlerValue(raw: unknown): unknown {
  if (Array.isArray(raw)) return raw[0]
  return raw
}

function pickMergedHandler(primary: unknown, secondary: unknown): unknown {
  const p = normalizeHandlerValue(primary)
  const s = normalizeHandlerValue(secondary)
  if (!isEmptyFormCreateHandler(p)) return p
  if (!isEmptyFormCreateHandler(s)) return s
  return p ?? s
}

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

function normalizeComponentEventHandler(raw: unknown): string {
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
