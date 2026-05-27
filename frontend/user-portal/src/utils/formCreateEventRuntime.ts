/**
 * Execute form-create designer "Form event" handlers (e.g. options.onChange) in User Portal.
 * Designer stores functions as [[FORM-CREATE-PREFIX-function ...}-FORM-CREATE-SUFFIX]] strings.
 */

const FC_FN_WRAPPER_RE =
  /^\[\[FORM-CREATE-PREFIX-function\s([\s\S]*)\}-FORM-CREATE-SUFFIX\]\]$/

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

  const wrapped = trimmed.match(FC_FN_WRAPPER_RE)
  if (wrapped) {
    return `function ${wrapped[1]}}`
  }
  if (trimmed.startsWith('function')) {
    return trimmed
  }
  return null
}

/**
 * Parse a form-create stored handler into a callable function.
 * Injects both `options` and `api` (same object) so designer scripts using either name work.
 */
export function parseFormCreateFunction(raw: unknown): FormCreateChangeHandler | null {
  if (typeof raw === 'function') {
    return wrapFormCreateHandler(raw as FormCreateChangeHandler)
  }
  if (typeof raw !== 'string') return null

  const source = normalizeFunctionSource(raw)
  if (!source || containsDangerousFormScript(source)) {
    return null
  }

  const body = extractFunctionBody(source)
  if (!body) return null

  try {
    const runner = new Function(
      'field',
      'value',
      'options',
      'api',
      body,
    ) as (field: string, value: unknown, options: PortalFormApi, api: PortalFormApi) => void

    return (field, value, portalApi) => {
      runner(field, value, portalApi, portalApi)
    }
  } catch (err) {
    console.warn('[formCreateEventRuntime] Failed to parse handler:', err)
    return null
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

export function createPortalFormApi(
  getFormData: () => Record<string, unknown>,
  applyPatch: (patch: Record<string, unknown>) => void,
  resolveFieldKey?: FieldKeyResolver,
  visibility?: {
    state: PortalFormVisibilityState
    notify: () => void
    getAllFieldKeys: () => string[]
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
  }
}

export function runFormOnChangeHandler(
  rawHandler: unknown,
  field: string,
  value: unknown,
  portalApi: PortalFormApi,
): void {
  const handler = parseFormCreateFunction(rawHandler)
  if (!handler) return
  try {
    handler(field, value, portalApi)
  } catch (err) {
    console.warn('[formCreateEventRuntime] onChange execution error:', err)
  }
}
