/**
 * Execute form-create designer "Form event" handlers (e.g. options.onChange) in User Portal.
 * Designer stores functions as [[FORM-CREATE-PREFIX-function ...}-FORM-CREATE-SUFFIX]] strings.
 */

import { isEmptyFormCreateHandler } from '@platform-shared/formEvents/script'
import { createFormEventParser } from '@platform-shared/formEvents/parser'
import { createFormEventOptionsBridge, type FormCreateChangeHandler, type PortalFormApi } from './formCreateEventApi'
import { readFormEventCurrentUser } from './formCreateEventUser'

// Re-export the script primitives, API surface, and validator runtime so existing
// `@/utils/formCreateEventRuntime` imports keep working unchanged (single entry point).
export {
  containsDangerousFormScript,
  isEmptyFormCreateHandler,
} from './formCreateEventScript'
export {
  createFieldKeyResolver,
  createFormEventOptionsBridge,
  createPortalFormApi,
  isEffectivelyDisabled,
  isEffectivelyRequired,
  overlayEventRequiredOnFormRules,
  type FieldKeyResolver,
  type FormCreateChangeHandler,
  type FormCreateEventContext,
  type FormEventOptionsBridge,
  type PortalFormApi,
  type FormEventChoiceOption,
  type FormEventLookupFilter,
  type FormEventNotification,
  type PortalFormApiOverlays,
  type PortalFormRequiredBag,
  type PortalFormRequiredState,
  type PortalFormVisibilityState,
} from './formCreateEventApi'
export {
  bindFormCreateValidatorForElementPlus,
  parseFormCreateValidator,
  type FormCreateValidatorArity,
  type ParsedFormCreateValidator,
} from './formCreateEventValidator'

/**
 * Parse a form-create stored handler (form-level or component-level).
 * Injects options/api/rule/self for designer EVENT panel scripts.
 */
export const parseFormCreateEventHandler = createFormEventParser(readFormEventCurrentUser)

/**
 * Parse a form-create stored handler into a callable function.
 * Injects both `options` and `api` (same object) so designer scripts using either name work.
 */
export function parseFormCreateFunction(raw: unknown): FormCreateChangeHandler | null {
  const handler = parseFormCreateEventHandler(raw)
  if (!handler) return null
  return (field, value, portalApi) => {
    return handler({ field, value, api: portalApi, rule: {} })
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
    return runFormOnChangeHandler(stored, field, value, options, bag.rule ?? {})
  }
}

export function runFormOnChangeHandler(
  rawHandler: unknown,
  field: string,
  value: unknown,
  portalApi: PortalFormApi,
  rule: Record<string, unknown> = {},
  args: unknown[] = [field, value, { api: portalApi, rule }],
): unknown {
  const handler = parseFormCreateEventHandler(rawHandler)
  if (isEmptyFormCreateHandler(rawHandler)) return undefined
  if (!handler) throw new Error('Invalid form event script')
  return handler({ field, value, api: portalApi, rule, args })
}
