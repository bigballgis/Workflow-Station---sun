/**
 * Execute form-create designer "Form event" handlers (e.g. options.onChange) in User Portal.
 * Designer stores functions as [[FORM-CREATE-PREFIX-function ...}-FORM-CREATE-SUFFIX]] strings.
 */

const FC_FN_WRAPPER_RE =
  /^\[\[FORM-CREATE-PREFIX-function\s([\s\S]*)\}-FORM-CREATE-SUFFIX\]\]$/
const FC_FNX_PREFIX = '$FNX:'

const DANGEROUS_KEYWORDS = /\b(eval|Function|import|require|window|document|globalThis|process)\b/

export interface PortalFormApi {
  setValue: (fieldOrData: string | Record<string, unknown>, value?: unknown) => void
  getValue: (field: string) => unknown
  /** form-create: `hidden(true, field)` hides (no DOM); `hidden(false, field)` shows. */
  hidden: (status: boolean, field?: string | string[]) => void
  /** form-create: `display(true, field)` hides (CSS); `display(false, field)` shows. */
  display: (status: boolean, field?: string | string[]) => void
  hiddenStatus: (field: string) => boolean
  displayStatus: (field: string) => boolean
  /** Show inline error under a field (Element Plus form-item). */
  setFieldError: (field: string, message: string) => void
  /** Clear script-injected error for one field. */
  clearFieldError: (field: string) => void
  readonly form: Record<string, unknown>
}

export interface PortalFormVisibilityState {
  /** true = field removed from DOM (form-create `hidden`). */
  hidden: Map<string, boolean>
  /** false = field rendered but not visible (form-create `display`). */
  display: Map<string, boolean>
}

/** Map designer script field names to bound keys (e.g. label "test2" → key "tes2"). */
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

/**
 * Parse a form-create stored handler (form-level or component-level).
 * Injects options/api/rule/self for designer EVENT panel scripts.
 */
export function parseFormCreateEventHandler(raw: unknown): ((ctx: FormCreateEventContext) => void) | null {
  if (typeof raw === 'function') {
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
      const runner = new Function('$inject', body) as (inject: Record<string, unknown>) => void
      return (ctx) => {
        runner({
          api: ctx.api,
          rule: ctx.rule,
          self: ctx.rule,
          options: ctx.api,
          option: {},
          args: ctx.args ?? [],
          field: ctx.field,
          value: ctx.value,
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
    ) => void

    return (ctx) => {
      const options = createFormEventOptionsBridge(ctx.api, ctx.rule)
      runner(
        ctx.field,
        ctx.value,
        options,
        ctx.api,
        ctx.rule,
        ctx.rule,
        {},
        ctx.args,
      )
    }
  } catch (err) {
    console.warn('[formCreateEventRuntime] Failed to parse handler:', err)
    return null
  }
}

/**
 * Parse a form-create stored handler into a callable function.
 * Injects both `options` and `api` (same object) so designer scripts using either name work.
 */
export function parseFormCreateFunction(raw: unknown): FormCreateChangeHandler | null {
  const handler = parseFormCreateEventHandler(raw)
  if (!handler) return null
  return (field, value, portalApi) => {
    handler({ field, value, api: portalApi, rule: {} })
  }
}

function wrapFormCreateHandler(fn: FormCreateChangeHandler): FormCreateChangeHandler {
  return (field, value, portalApi) => {
    fn(field, value, portalApi)
  }
}

function resolveFieldTargets(
  field: string | string[] | undefined,
  resolve: (key: string) => string,
  getAllFieldKeys: () => string[],
): string[] {
  if (field === undefined) return getAllFieldKeys()
  const list = Array.isArray(field) ? field : [field]
  return list.map(resolve)
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
  }
}

/**
 * Wrap persisted form-level onChange for form-create runtime.
 * form-create invokes onChange(field, value, { api, rule, setFlag }) — not PortalFormApi directly.
 */
export function wrapFormLevelOnChangeForFormCreate(raw: unknown): unknown {
  if (isEmptyFormCreateHandler(raw)) return raw
  const stored = raw
  return function formLevelOnChange(field: string, value: unknown, inject?: unknown) {
    const bag =
      inject && typeof inject === 'object'
        ? (inject as { api?: PortalFormApi; rule?: Record<string, unknown> })
        : {}
    const fcApi = bag.api && typeof bag.api.getValue === 'function' ? bag.api : null
    if (!fcApi) return
    const options = createFormEventOptionsBridge(fcApi, bag.rule)
    runFormOnChangeHandler(stored, field, value, options, bag.rule ?? {})
  }
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
): PortalFormApi {
  const resolve = (key: string) => resolveFieldKey?.(key) ?? key
  const vis = visibility?.state

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
      if (!vis) return
      for (const key of resolveFieldTargets(field, resolve, visibility!.getAllFieldKeys)) {
        if (status) vis.hidden.set(key, true)
        else vis.hidden.delete(key)
      }
      visibility!.notify()
    },
    display(status: boolean, field?: string | string[]) {
      if (!vis) return
      for (const key of resolveFieldTargets(field, resolve, visibility!.getAllFieldKeys)) {
        if (status) vis.display.set(key, false)
        else vis.display.delete(key)
      }
      visibility!.notify()
    },
    hiddenStatus(field: string) {
      return vis?.hidden.get(resolve(field)) === true
    },
    displayStatus(field: string) {
      const key = resolve(field)
      if (vis?.display.has(key)) return vis.display.get(key) !== false
      return true
    },
    setFieldError(field: string, message: string) {
      fieldErrors?.setFieldError(resolve(field), message)
    },
    clearFieldError(field: string) {
      fieldErrors?.clearFieldError(resolve(field))
    },
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

export type FormCreateValidatorArity = 'adapter' | 'valueRuleCallback' | 'ruleValueCallback' | 'inject'

export interface ParsedFormCreateValidator {
  arity: FormCreateValidatorArity
  run: (
    ctx: { api: PortalFormApi; rule: Record<string, unknown> },
    rule: unknown,
    value: unknown,
    callback: (err?: Error | string) => void,
  ) => void
}

function detectValidatorArity(
  source: string,
  body: string,
  adapter?: boolean,
): FormCreateValidatorArity {
  if (/\$inject\b/.test(body) || /function\s*\(\s*\$inject\s*\)/.test(source)) return 'inject'
  const match = source.match(/function\s*\w*\s*\(\s*([^)]*)\)\s*\{/)
  if (match) {
    const params = match[1]
      .split(',')
      .map((p) => p.trim().replace(/^\.{3}/, '').split(':')[0].trim())
      .filter(Boolean)
    // Prefer explicit parameter count over adapter flag — fc-designer validate-v2 sets
    // adapter:true even when the editor stores (rule, value, callback).
    if (params.length === 2) return 'adapter'
    if (params.length >= 3) {
      const first = params[0]
      if (/^(val|value)$/i.test(first)) return 'valueRuleCallback'
      return 'ruleValueCallback'
    }
  }
  if (adapter === true) return 'adapter'
  return 'ruleValueCallback'
}

/**
 * Parse designer-stored validator strings (FORM-CREATE-PREFIX / $FNX:) into a callable runner.
 * form-create Preview evaluates these natively; User Portal must parse and bind `this.api` at runtime.
 */
export function parseFormCreateValidator(
  raw: unknown,
  options?: { adapter?: boolean },
): ParsedFormCreateValidator | null {
  if (typeof raw === 'function') {
    const fn = raw as (
      this: { api: PortalFormApi; rule: Record<string, unknown> },
      ...args: unknown[]
    ) => void
    return {
      arity: 'ruleValueCallback',
      run: (ctx, rule, value, callback) => {
        fn.call({ api: ctx.api, rule: ctx.rule }, rule, value, callback)
      },
    }
  }
  if (typeof raw !== 'string' || isEmptyFormCreateHandler(raw)) return null

  const source = normalizeFunctionSource(raw)
  if (!source || containsDangerousFormScript(source)) return null
  const body = extractFunctionBody(source)
  if (body == null) return null

  const arity = detectValidatorArity(source, body, options?.adapter)

  try {
    if (arity === 'inject') {
      const runner = new Function('$inject', body) as (inject: Record<string, unknown>) => void
      return {
        arity,
        run: (ctx, rule, value, callback) => {
          runner({
            api: ctx.api,
            rule: ctx.rule,
            self: ctx.rule,
            options: ctx.api,
            value,
            callback,
          })
        },
      }
    }

    // Compile with parameter names matching designer arity — body references `value`/`callback`
    // by position; a fixed (rule,value,callback) wrapper breaks adapter (value,callback) scripts.
    const runner = (() => {
      switch (arity) {
        case 'adapter':
          return new Function('value', 'callback', body) as (
            this: { api: PortalFormApi; rule: Record<string, unknown> },
            value: unknown,
            callback: (err?: Error | string) => void,
          ) => void
        case 'valueRuleCallback':
          return new Function('value', 'rule', 'callback', body) as (
            this: { api: PortalFormApi; rule: Record<string, unknown> },
            value: unknown,
            rule: unknown,
            callback: (err?: Error | string) => void,
          ) => void
        default:
          return new Function('rule', 'value', 'callback', body) as (
            this: { api: PortalFormApi; rule: Record<string, unknown> },
            rule: unknown,
            value: unknown,
            callback: (err?: Error | string) => void,
          ) => void
      }
    })()

    return {
      arity,
      run: (ctx, rule, value, callback) => {
        const boundCtx = { api: ctx.api, rule: ctx.rule }
        switch (arity) {
          case 'adapter':
            runner.call(boundCtx, value, callback)
            break
          case 'valueRuleCallback':
            runner.call(boundCtx, value, rule, callback)
            break
          default:
            runner.call(boundCtx, rule, value, callback)
        }
      },
    }
  } catch (err) {
    console.warn('[formCreateEventRuntime] Failed to parse validator:', err)
    return null
  }
}

/** Bind a parsed designer validator to Element Plus / async-validator `(rule, value, callback)`. */
export function bindFormCreateValidatorForElementPlus(
  raw: unknown,
  getFormData: () => Record<string, unknown>,
  resolveFieldKey?: FieldKeyResolver,
  options?: { adapter?: boolean },
): ((rule: unknown, value: unknown, callback: (err?: Error | string) => void) => void) | null {
  const parsed = parseFormCreateValidator(raw, options)
  if (!parsed) return null
  const api = createPortalFormApi(getFormData, () => {}, resolveFieldKey)
  return (rule, value, callback) => {
    const bridgeCallback = (err?: Error | string) => {
      if (err == null || err === '') {
        callback()
        return
      }
      if (err instanceof Error) {
        callback(err)
        return
      }
      callback(new Error(String(err)))
    }
    try {
      parsed.run(
        { api, rule: (rule ?? {}) as Record<string, unknown> },
        rule,
        value,
        bridgeCallback,
      )
    } catch (err) {
      console.warn('[formCreateEventRuntime] validator execution error:', err)
      callback(new Error('Validation error'))
    }
  }
}
