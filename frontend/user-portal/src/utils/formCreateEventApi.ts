/**
 * Portal form API surface for form-create event scripts: the `PortalFormApi`
 * contract, visibility/field-key types, and the runtime bridges that back them.
 */

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

export interface PortalFormApi extends PortalFormApiOverlayMethods {
  setValue: (fieldOrData: string | Record<string, unknown>, value?: unknown) => void
  getValue: (field: string) => unknown
  /** form-create: `hidden(true, field)` hides (no DOM); `hidden(false, field)` shows. */
  hidden: (status: boolean, field?: string | string[]) => void
  /** form-create: `display(true, field)` hides (CSS); `display(false, field)` shows. */
  display: (status: boolean, field?: string | string[]) => void
  hiddenStatus: (field: string) => boolean
  displayStatus: (field: string) => boolean
  /** Show inline error under a field (Element Plus form-item). Blocks save until cleared. */
  setFieldError: (field: string, message: string) => void
  /** Clear script-injected error for one field. */
  clearFieldError: (field: string) => void
  /** form-create: `required(true, field)` forces required; `required(false, field)` forces optional. */
  required: (status: boolean, field?: string | string[]) => void
  /** True when a script last called `required(true, field)`. */
  requiredStatus: (field: string) => boolean
  readonly form: Record<string, unknown>
}

export interface PortalFormVisibilityState {
  /** true = field removed from DOM (form-create `hidden`). */
  hidden: Map<string, boolean>
  /** false = field rendered but not visible (form-create `display`). */
  display: Map<string, boolean>
}

/** Script overlay: true = force required, false = force optional (overrides designer / linkage). */
export interface PortalFormRequiredState {
  flags: Map<string, boolean>
}

export type PortalFormRequiredBag = {
  state: PortalFormRequiredState
  notify: () => void
  getAllFieldKeys: () => string[]
}

/** Missing flag → designer/linkage fallback; explicit true/false wins. */
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

/** Overlay `api.required` flags onto Element Plus form rules (add or strip required). */
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
  // Lazy read — keep in sync with developer-workstation formCreateEventRuntime
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
      for (const key of resolveFieldTargets(field, resolve, visibility.getAllFieldKeys)) {
        if (status) vis.hidden.set(key, true)
        else vis.hidden.delete(key)
      }
      visibility.notify()
    },
    display(status: boolean, field?: string | string[]) {
      const vis = visibilityState()
      if (!vis || !visibility) return
      for (const key of resolveFieldTargets(field, resolve, visibility.getAllFieldKeys)) {
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
      for (const key of resolveFieldTargets(field, resolve, bag.getAllFieldKeys)) {
        bag.state.flags.set(key, status)
      }
      bag.notify()
    },
    requiredStatus(field: string) {
      return required?.state?.flags.get(resolve(field)) === true
    },
    ...buildOverlayMethods(resolve, (field, getAll) => resolveFieldTargets(field, resolve, getAll), overlays),
  }
}
