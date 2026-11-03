import { readFormEventCurrentUser } from './formCreateEventUser'
/**
 * Execute form-create designer event handlers (Form Preview + keep in sync with user-portal).
 * Designer stores functions as [[FORM-CREATE-PREFIX-…]] or $FNX: + body.
 */

import { createFormEventOptionsBridge, type PortalFormApi } from '@platform-shared/formEvents/api'
import { isEmptyFormCreateHandler } from '@platform-shared/formEvents/script'
import { createFormEventParser } from '@platform-shared/formEvents/parser'
export * from '@platform-shared/formEvents/api'
export { containsDangerousFormScript, isEmptyFormCreateHandler } from '@platform-shared/formEvents/script'
export const parseFormCreateEventHandler = createFormEventParser(readFormEventCurrentUser)

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
