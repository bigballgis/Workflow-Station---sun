/**
 * Execute form-create designer "Form event" handlers (e.g. options.onChange) in User Portal.
 * Designer stores functions as [[FORM-CREATE-PREFIX-function ...}-FORM-CREATE-SUFFIX]] strings.
 */

import {
  containsDangerousFormScript,
  extractFunctionBody,
  isEmptyFormCreateHandler,
  normalizeFunctionSource,
} from './formCreateEventScript'
import {
  createFormEventOptionsBridge,
  type FormCreateChangeHandler,
  type FormCreateEventContext,
  type PortalFormApi,
} from './formCreateEventApi'

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
  type FieldKeyResolver,
  type FormCreateChangeHandler,
  type FormCreateEventContext,
  type FormEventOptionsBridge,
  type PortalFormApi,
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
export function parseFormCreateEventHandler(raw: unknown): ((ctx: FormCreateEventContext) => void) | null {
  if (typeof raw === 'function') {
    // Prefer tagged $FNX: / FORM-CREATE source so callers can pass a fresh ctx.api
    // (e.g. visibility-wired PortalFormApi) instead of a stale closed-over api.
    // Keep in sync with developer-workstation/src/utils/formCreateEventRuntime.ts
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
      // formData/data: Form-level onSubmit / beforeSubmit param names.
      // Keep in sync with developer-workstation/src/utils/formCreateEventRuntime.ts
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

export function runFormOnChangeHandler(
  rawHandler: unknown,
  field: string,
  value: unknown,
  portalApi: PortalFormApi,
  rule: Record<string, unknown> = {},
): unknown {
  const handler = parseFormCreateEventHandler(rawHandler)
  if (!handler || isEmptyFormCreateHandler(rawHandler)) return undefined
  try {
    return handler({ field, value, api: portalApi, rule })
  } catch (err) {
    console.warn('[formCreateEventRuntime] onChange execution error:', err)
    return undefined
  }
}
