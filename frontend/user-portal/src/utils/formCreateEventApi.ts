/**
 * Portal form API surface for form-create event scripts: the `PortalFormApi`
 * contract, visibility/field-key types, and the runtime bridges that back them.
 */

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
  }
}
