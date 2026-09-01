/**
 * Extra form-event api overlays (disabled, options, notifications, lookup filter,
 * labels, focus). Keep in sync with user-portal formCreateEventOverlays.ts
 */

export type FormEventChoiceOption = { label: string; value: string | number }

export type FormEventNotificationLevel = 'ERROR' | 'WARNING' | 'INFO'

export interface FormEventNotification {
  uniqueId: string
  level: FormEventNotificationLevel
  message: string
}

export interface FormEventLookupFilter {
  fieldName: string
  value: string
  matchType?: 'eq' | 'contains' | 'startsWith' | 'endsWith'
}

export interface PortalFormDisabledState {
  flags: Map<string, boolean>
}

export function isEffectivelyDisabled(
  fieldKey: string,
  fallback: boolean,
  flags?: Map<string, boolean> | null,
): boolean {
  if (flags?.has(fieldKey)) return flags.get(fieldKey) === true
  return fallback
}

export interface PortalFormApiOverlays {
  disabled?: {
    state: PortalFormDisabledState
    notify: () => void
    getAllFieldKeys: () => string[]
  }
  options?: {
    state: Map<string, FormEventChoiceOption[]>
    notify: () => void
    getDesignerOptions: (fieldKey: string) => FormEventChoiceOption[]
  }
  labels?: {
    state: Map<string, string>
    notify: () => void
    getDesignerLabel: (fieldKey: string) => string
  }
  notifications?: {
    set: (item: FormEventNotification) => void
    clear: (uniqueId: string) => void
  }
  lookupFilter?: {
    set: (fieldKey: string, conditions: FormEventLookupFilter[]) => void
    clear: (fieldKey: string) => void
    refresh: (fieldKey: string) => void
  }
  focus?: (fieldKey: string) => void
}

export interface PortalFormApiOverlayMethods {
  disabled: (status: boolean, field?: string | string[]) => void
  disabledStatus: (field: string) => boolean
  setOptions: (field: string, options: FormEventChoiceOption[]) => void
  addOption: (field: string, option: FormEventChoiceOption) => void
  removeOption: (field: string, value: string | number) => void
  clearOptions: (field: string) => void
  resetOptions: (field: string) => void
  setFormNotification: (message: string, level: FormEventNotificationLevel, uniqueId: string) => void
  clearFormNotification: (uniqueId: string) => void
  setLookupFilter: (field: string, conditions: FormEventLookupFilter[]) => void
  clearLookupFilter: (field: string) => void
  refresh: (field: string) => void
  setFocus: (field: string) => void
  setLabel: (field: string, text: string) => void
  getLabel: (field: string) => string
  resetLabel: (field: string) => void
}

function currentOptions(
  overlays: PortalFormApiOverlays | undefined,
  key: string,
): FormEventChoiceOption[] {
  const bag = overlays?.options
  if (!bag) return []
  if (bag.state.has(key)) return [...(bag.state.get(key) ?? [])]
  return [...bag.getDesignerOptions(key)]
}

function buildLockAndChoiceMethods(
  resolve: (key: string) => string,
  resolveTargets: (field: string | string[] | undefined, getAll: () => string[]) => string[],
  overlays?: PortalFormApiOverlays,
): Pick<
  PortalFormApiOverlayMethods,
  | 'disabled'
  | 'disabledStatus'
  | 'setOptions'
  | 'addOption'
  | 'removeOption'
  | 'clearOptions'
  | 'resetOptions'
> {
  return {
    disabled(status, field) {
      const bag = overlays?.disabled
      if (!bag?.state) return
      for (const key of resolveTargets(field, bag.getAllFieldKeys)) {
        bag.state.flags.set(key, status)
      }
      bag.notify()
    },
    disabledStatus(field) {
      return overlays?.disabled?.state.flags.get(resolve(field)) === true
    },
    setOptions(field, options) {
      const bag = overlays?.options
      if (!bag) return
      bag.state.set(resolve(field), [...options])
      bag.notify()
    },
    addOption(field, option) {
      const bag = overlays?.options
      if (!bag) return
      const key = resolve(field)
      bag.state.set(key, [...currentOptions(overlays, key), option])
      bag.notify()
    },
    removeOption(field, value) {
      const bag = overlays?.options
      if (!bag) return
      const key = resolve(field)
      bag.state.set(key, currentOptions(overlays, key).filter((item) => item.value !== value))
      bag.notify()
    },
    clearOptions(field) {
      const bag = overlays?.options
      if (!bag) return
      bag.state.set(resolve(field), [])
      bag.notify()
    },
    resetOptions(field) {
      overlays?.options?.state.delete(resolve(field))
      overlays?.options?.notify()
    },
  }
}

function buildNoticeAndChromeMethods(
  resolve: (key: string) => string,
  overlays?: PortalFormApiOverlays,
): Omit<
  PortalFormApiOverlayMethods,
  | 'disabled'
  | 'disabledStatus'
  | 'setOptions'
  | 'addOption'
  | 'removeOption'
  | 'clearOptions'
  | 'resetOptions'
> {
  return {
    setFormNotification(message, level, uniqueId) {
      const text = String(message ?? '')
      const id = String(uniqueId ?? '')
      if (!text || !id) return
      overlays?.notifications?.set({ uniqueId: id, level, message: text })
    },
    clearFormNotification(uniqueId) {
      overlays?.notifications?.clear(String(uniqueId ?? ''))
    },
    setLookupFilter(field, conditions) {
      overlays?.lookupFilter?.set(resolve(field), Array.isArray(conditions) ? conditions : [])
    },
    clearLookupFilter(field) {
      overlays?.lookupFilter?.clear(resolve(field))
    },
    refresh(field) {
      overlays?.lookupFilter?.refresh(resolve(field))
    },
    setFocus(field) {
      overlays?.focus?.(resolve(field))
    },
    setLabel(field, text) {
      const bag = overlays?.labels
      if (!bag) return
      bag.state.set(resolve(field), String(text ?? ''))
      bag.notify()
    },
    getLabel(field) {
      const bag = overlays?.labels
      const key = resolve(field)
      if (bag?.state.has(key)) return bag.state.get(key) ?? ''
      return bag?.getDesignerLabel(key) ?? ''
    },
    resetLabel(field) {
      overlays?.labels?.state.delete(resolve(field))
      overlays?.labels?.notify()
    },
  }
}

/** AND designer/cascade filters with a script overlay (script last). */
export function mergeScriptLookupFilters(
  base: Array<{ fieldName: string; value: string; matchType?: FormEventLookupFilter['matchType'] }>,
  script: FormEventLookupFilter[] | undefined,
): Array<{ fieldName: string; value: string; matchType?: FormEventLookupFilter['matchType'] }> {
  if (!script?.length) return base
  return [
    ...base,
    ...script.map((c) => ({
      fieldName: String(c.fieldName ?? ''),
      value: String(c.value ?? ''),
      matchType: c.matchType,
    })),
  ]
}

export function buildOverlayMethods(
  resolve: (key: string) => string,
  resolveTargets: (field: string | string[] | undefined, getAll: () => string[]) => string[],
  overlays?: PortalFormApiOverlays,
): PortalFormApiOverlayMethods {
  return {
    ...buildLockAndChoiceMethods(resolve, resolveTargets, overlays),
    ...buildNoticeAndChromeMethods(resolve, overlays),
  }
}

export function forwardOverlayMethods(
  api: PortalFormApiOverlayMethods,
): PortalFormApiOverlayMethods {
  return {
    disabled: (status, field) => api.disabled(status, field),
    disabledStatus: (field) => api.disabledStatus(field),
    setOptions: (field, options) => api.setOptions(field, options),
    addOption: (field, option) => api.addOption(field, option),
    removeOption: (field, value) => api.removeOption(field, value),
    clearOptions: (field) => api.clearOptions(field),
    resetOptions: (field) => api.resetOptions(field),
    setFormNotification: (message, level, uniqueId) =>
      api.setFormNotification(message, level, uniqueId),
    clearFormNotification: (uniqueId) => api.clearFormNotification(uniqueId),
    setLookupFilter: (field, conditions) => api.setLookupFilter(field, conditions),
    clearLookupFilter: (field) => api.clearLookupFilter(field),
    refresh: (field) => api.refresh(field),
    setFocus: (field) => api.setFocus(field),
    setLabel: (field, text) => api.setLabel(field, text),
    getLabel: (field) => api.getLabel(field),
    resetLabel: (field) => api.resetLabel(field),
  }
}
