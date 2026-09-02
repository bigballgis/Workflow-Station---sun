/**
 * Execute form-create designer event handlers (Form Preview + keep in sync with user-portal).
 * Designer stores functions as [[FORM-CREATE-PREFIX-…]] or $FNX: + body.
 */

import { readFormEventCurrentUser } from './formCreateEventUser'
import {
  buildOverlayMethods,
  forwardOverlayMethods,
  type PortalFormApiOverlayMethods,
  type PortalFormApiOverlays,
} from './formCreateEventOverlays'

export type {
  FormEventChoiceOption,
  FormEventLookupFilter,
  FormEventNotification,
  FormEventNotificationLevel,
  PortalFormApiOverlays,
  PortalFormDisabledState,
} from './formCreateEventOverlays'
export { isEffectivelyDisabled } from './formCreateEventOverlays'

const FC_FN_WRAPPER_RE =
  /^\[\[FORM-CREATE-PREFIX-function\s([\s\S]*)\}-FORM-CREATE-SUFFIX\]\]$/
const FC_FNX_PREFIX = '$FNX:'

const DANGEROUS_KEYWORDS = /\b(eval|Function|import|require|window|document|globalThis|process)\b/

export interface PortalFormApi extends PortalFormApiOverlayMethods {
  setValue: (fieldOrData: string | Record<string, unknown>, value?: unknown) => void
  getValue: (field: string) => unknown
  hidden: (status: boolean, field?: string | string[]) => void
  display: (status: boolean, field?: string | string[]) => void
  hiddenStatus: (field: string) => boolean
  displayStatus: (field: string) => boolean
  setFieldError: (field: string, message: string) => void
  clearFieldError: (field: string) => void
  required: (status: boolean, field?: string | string[]) => void
  requiredStatus: (field: string) => boolean
  readonly form: Record<string, unknown>
}

export interface PortalFormVisibilityState {
  hidden: Map<string, boolean>
  display: Map<string, boolean>
}

export interface PortalFormRequiredState {
  flags: Map<string, boolean>
}

export type PortalFormRequiredBag = {
  state: PortalFormRequiredState
  notify: () => void
  getAllFieldKeys: () => string[]
}

export function isEffectivelyRequired(
  fieldKey: string,
  fallback: boolean,
  flags?: Map<string, boolean> | null,
): boolean {
  if (flags?.has(fieldKey)) return flags.get(fieldKey) === true
  return fallback
}

function isRequiredRuleEntry(entry: unknown): boolean {
  if (!entry || typeof entry !== 'object') return false
  const item = entry as Record<string, unknown>
  return item.required === true || item.mode === 'required'
}

export function overlayEventRequiredOnFormRules<T extends Record<string, unknown>>(
  base: T,
  fieldKeys: string[],
  flags: Map<string, boolean> | undefined,
  makeRequiredRule: (fieldKey: string) => unknown[],
): T {
  if (!flags || flags.size === 0) return base
  const out = { ...base } as T
  for (const key of fieldKeys) {
    const flag = flags.get(key)
    if (flag === undefined) continue
    const existing = Array.isArray(out[key as keyof T])
      ? [...(out[key as keyof T] as unknown[])]
      : []
    if (flag === true) {
      if (!existing.some(isRequiredRuleEntry)) {
        (out as Record<string, unknown>)[key] = [...makeRequiredRule(key), ...existing]
      }
    } else {
      const stripped = existing.filter((entry) => !isRequiredRuleEntry(entry))
      if (stripped.length > 0) (out as Record<string, unknown>)[key] = stripped
      else delete (out as Record<string, unknown>)[key]
    }
  }
  return out
}

export type FieldKeyResolver = (name: string) => string

export function createFieldKeyResolver(
  getFields: () => Array<{ key: string; label?: string }>,
): FieldKeyResolver {
  return (name: string) => {
    const fields = getFields()
    if (fields.some(f => f.key === name)) return name
    const byLabel = fields.find(f => f.label === name)
    return byLabel?.key ?? name
  }
}

export type FormCreateChangeHandler = (
  field: string,
  value: unknown,
  api: PortalFormApi,
) => void

export interface FormCreateEventContext {
  field: string
  value: unknown
  api: PortalFormApi
  rule: Record<string, unknown>
  args?: unknown
}

export function containsDangerousFormScript(source: string): boolean {
  return DANGEROUS_KEYWORDS.test(source)
}

function extractFunctionBody(source: string): string | null {
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

function normalizeFunctionSource(raw: string): string | null {
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

export function parseFormCreateEventHandler(raw: unknown): ((ctx: FormCreateEventContext) => void) | null {
  if (typeof raw === 'function') {
    // Preview materialize wraps $FNX: into functions that close over a build-time API
    // (often without visibility). Prefer the tagged source so dispatch can pass ctx.api
    // with api.hidden/display wired to extracted lookup / subTable.
    // Keep in sync with user-portal/src/utils/formCreateEventRuntime.ts
    const tagged = raw as { __hermesFormEventSource?: unknown; __json?: unknown }
    const source = tagged.__hermesFormEventSource ?? tagged.__json
    if (typeof source === 'string' && !isEmptyFormCreateHandler(source)) {
      return parseFormCreateEventHandler(source)
    }
    return (ctx) => {
      try {
        (raw as FormCreateChangeHandler)(ctx.field, ctx.value, ctx.api)
      } catch (err) {
        console.warn('[formCreateEventRuntime] handler error:', err)
      }
    }
  }
  if (typeof raw !== 'string') return null

  const source = normalizeFunctionSource(raw)
  if (!source || containsDangerousFormScript(source)) {
    return null
  }

  const body = extractFunctionBody(source)
  if (body == null) return null

  const usesInject = /\$inject\b/.test(body)
    || /function\s*\(\s*\$inject\s*\)/.test(source)

  try {
    if (usesInject) {
      // $FNX: bodies are normalized to function($inject){…}. Designer scripts often use
      // bare `api` / `value` (form-create docs) as well as `$inject.api` — bind both.
      // Keep in sync with user-portal/src/utils/formCreateEventRuntime.ts
      const runner = new Function(
        '$inject',
        [
          'var api = $inject.api;',
          'var options = $inject.options;',
          'var option = $inject.option;',
          'var rule = $inject.rule;',
          'var self = $inject.self;',
          'var args = $inject.args;',
          'var field = $inject.field;',
          'var value = $inject.value;',
          'var formData = $inject.formData;',
          'var data = $inject.data;',
          'var user = $inject.user;',
          body,
        ].join('\n'),
      ) as (inject: Record<string, unknown>) => unknown
      return (ctx) => {
        const formSnapshot = ctx.api.form
        return runner({
          api: ctx.api,
          rule: ctx.rule,
          self: ctx.rule,
          options: ctx.api,
          option: {},
          args: ctx.args ?? [],
          field: ctx.field,
          value: ctx.value,
          formData: formSnapshot,
          data: formSnapshot,
          user: readFormEventCurrentUser(),
        })
      }
    }

    const runner = new Function(
      'field',
      'value',
      'options',
      'api',
      'rule',
      'self',
      'option',
      'args',
      'formData',
      'data',
      'user',
      body,
    ) as (
      field: string,
      value: unknown,
      options: PortalFormApi,
      api: PortalFormApi,
      rule: Record<string, unknown>,
      self: Record<string, unknown>,
      option: Record<string, unknown>,
      args: unknown,
      formData: Record<string, unknown>,
      data: Record<string, unknown>,
      user: ReturnType<typeof readFormEventCurrentUser>,
    ) => unknown

    return (ctx) => {
      const options = createFormEventOptionsBridge(ctx.api, ctx.rule)
      const formSnapshot = ctx.api.form
      return runner(
        ctx.field,
        ctx.value,
        options,
        ctx.api,
        ctx.rule,
        ctx.rule,
        {},
        ctx.args,
        formSnapshot,
        formSnapshot,
        readFormEventCurrentUser(),
      )
    }
  } catch (err) {
    console.warn('[formCreateEventRuntime] Failed to parse handler:', err)
    return null
  }
}

/** Form event scripts use `options`; form-create passes `{ api, rule }` as the 3rd arg. */
export type FormEventOptionsBridge = PortalFormApi & {
  api: PortalFormApi
  rule?: Record<string, unknown>
}

export function createFormEventOptionsBridge(
  api: PortalFormApi,
  rule?: Record<string, unknown>,
): FormEventOptionsBridge {
  return {
    api,
    rule,
    get form() {
      return api.form
    },
    getValue: (field: string) => api.getValue(field),
    setValue: (fieldOrData: string | Record<string, unknown>, value?: unknown) =>
      api.setValue(fieldOrData, value),
    hidden: (status: boolean, field?: string | string[]) => api.hidden(status, field),
    display: (status: boolean, field?: string | string[]) => api.display(status, field),
    hiddenStatus: (field: string) => api.hiddenStatus(field),
    displayStatus: (field: string) => api.displayStatus(field),
    setFieldError: (field: string, message: string) => api.setFieldError(field, message),
    clearFieldError: (field: string) => api.clearFieldError(field),
    required: (status: boolean, field?: string | string[]) => {
      if (typeof api.required === 'function') {
        api.required(status, field)
        return
      }
      applyNativeFcRequiredFallback(api, status, field)
    },
    requiredStatus: (field: string) => {
      if (typeof api.requiredStatus === 'function') return api.requiredStatus(field)
      return false
    },
    ...forwardOverlayMethods(api),
  }
}

function applyNativeFcRequiredFallback(
  api: PortalFormApi,
  status: boolean,
  field?: string | string[],
): void {
  const fc = api as PortalFormApi & {
    mergeRule?: (f: string, rule: Record<string, unknown>) => void
    setEffect?: (f: string, attr: string, value: unknown) => void
    sync?: (f: string) => void
  }
  if (field === undefined) return
  const keys = Array.isArray(field) ? field : [field]
  for (const key of keys) {
    if (typeof fc.setEffect === 'function') fc.setEffect.call(api, key, 'required', status)
    if (typeof fc.mergeRule === 'function') fc.mergeRule.call(api, key, { $required: status })
    if (typeof fc.sync === 'function') fc.sync.call(api, key)
  }
}

/**
 * Wrap persisted form-level onChange for form-create runtime.
 * form-create invokes onChange(field, value, { api, rule, setFlag }) — not PortalFormApi directly.
 */
export function wrapFormLevelOnChangeForFormCreate(raw: unknown): unknown {
  if (isEmptyFormCreateHandler(raw)) return raw
  const stored = raw
  const fn = function formLevelOnChange(field: string, value: unknown, inject?: unknown) {
    const bag =
      inject && typeof inject === 'object'
        ? (inject as { api?: PortalFormApi; rule?: Record<string, unknown> })
        : {}
    const fcApi = bag.api && typeof bag.api.getValue === 'function' ? bag.api : null
    if (!fcApi) {
      console.warn('[formCreateEventRuntime] missing form-create api for form-level onChange', field)
      return
    }
    const options = createFormEventOptionsBridge(fcApi, bag.rule)
    runFormOnChangeHandler(stored, field, value, options, bag.rule ?? {})
  }
  ;(fn as FormLevelOnChangeWrapper).__hermesFormEventSource = stored
  return fn
}

type FormLevelOnChangeWrapper = ((field: string, value: unknown, inject?: unknown) => void) & {
  __hermesFormEventSource?: unknown
}

export function createPortalFormApi(
  getFormData: () => Record<string, unknown>,
  applyPatch: (patch: Record<string, unknown>) => void,
  resolveFieldKey?: FieldKeyResolver,
  visibility?: {
    state: PortalFormVisibilityState
    notify: () => void
    getAllFieldKeys: () => string[]
  },
  fieldErrors?: {
    setFieldError: (fieldKey: string, message: string) => void
    clearFieldError: (fieldKey: string) => void
  },
  required?: PortalFormRequiredBag,
  overlays?: PortalFormApiOverlays,
): PortalFormApi {
  const resolve = (key: string) => resolveFieldKey?.(key) ?? key

  function resolveFieldTargets(
    field: string | string[] | undefined,
    getAllFieldKeys: () => string[],
  ): string[] {
    if (field === undefined) return getAllFieldKeys()
    const list = Array.isArray(field) ? field : [field]
    return list.map(resolve)
  }

  /** Lazy: Preview materialize binds before FormPreviewItems registers the bridge. */
  function visibilityState(): PortalFormVisibilityState | undefined {
    return visibility?.state
  }

  return {
    get form() {
      return getFormData()
    },
    getValue(field: string) {
      return getFormData()[resolve(field)]
    },
    setValue(fieldOrData: string | Record<string, unknown>, value?: unknown) {
      if (typeof fieldOrData === 'object' && fieldOrData !== null && !Array.isArray(fieldOrData)) {
        const resolved: Record<string, unknown> = {}
        for (const [k, v] of Object.entries(fieldOrData as Record<string, unknown>)) {
          resolved[resolve(k)] = v
        }
        applyPatch(resolved)
        return
      }
      if (typeof fieldOrData === 'string') {
        applyPatch({ [resolve(fieldOrData)]: value })
      }
    },
    hidden(status: boolean, field?: string | string[]) {
      const vis = visibilityState()
      if (!vis || !visibility) return
      for (const key of resolveFieldTargets(field, visibility.getAllFieldKeys)) {
        if (status) vis.hidden.set(key, true)
        else vis.hidden.delete(key)
      }
      visibility.notify()
    },
    display(status: boolean, field?: string | string[]) {
      const vis = visibilityState()
      if (!vis || !visibility) return
      for (const key of resolveFieldTargets(field, visibility.getAllFieldKeys)) {
        if (status) vis.display.set(key, false)
        else vis.display.delete(key)
      }
      visibility.notify()
    },
    hiddenStatus(field: string) {
      return visibilityState()?.hidden.get(resolve(field)) === true
    },
    displayStatus(field: string) {
      const key = resolve(field)
      const vis = visibilityState()
      if (vis?.display.has(key)) return vis.display.get(key) !== false
      return true
    },
    setFieldError(field: string, message: string) {
      fieldErrors?.setFieldError(resolve(field), message)
    },
    clearFieldError(field: string) {
      fieldErrors?.clearFieldError(resolve(field))
    },
    required(status: boolean, field?: string | string[]) {
      const bag = required
      if (!bag?.state) return
      for (const key of resolveFieldTargets(field, bag.getAllFieldKeys)) {
        bag.state.flags.set(key, status)
      }
      bag.notify()
    },
    requiredStatus(field: string) {
      return required?.state?.flags.get(resolve(field)) === true
    },
    ...buildOverlayMethods(resolve, (field, getAll) => resolveFieldTargets(field, getAll), overlays),
  }
}

export function runFormOnChangeHandler(
  rawHandler: unknown,
  field: string,
  value: unknown,
  portalApi: PortalFormApi,
  rule: Record<string, unknown> = {},
): void {
  const handler = parseFormCreateEventHandler(rawHandler)
  if (!handler || isEmptyFormCreateHandler(rawHandler)) return
  try {
    handler({ field, value, api: portalApi, rule })
  } catch (err) {
    console.warn('[formCreateEventRuntime] onChange execution error:', err)
  }
}
