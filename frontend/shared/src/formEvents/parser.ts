import { containsDangerousFormScript, extractFunctionBody, isEmptyFormCreateHandler, normalizeFunctionSource } from './script'
import { createFormEventOptionsBridge, type FormCreateChangeHandler, type FormCreateEventContext, type PortalFormApi } from './api'

/** Shared script compiler; identity comes from the hosting application's session. */
export function createFormEventParser(readFormEventCurrentUser: () => unknown) {
function parseFormCreateEventHandler(raw: unknown): ((ctx: FormCreateEventContext) => void) | null {
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
          'var user = $inject.user;',
          'var config = $inject.config;',
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
          data: ctx.data === undefined ? formSnapshot : ctx.data,
          config: ctx.config,
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
      'config',
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
      data: unknown,
      user: ReturnType<typeof readFormEventCurrentUser>,
      config: unknown,
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
        ctx.data === undefined ? formSnapshot : ctx.data,
        readFormEventCurrentUser(),
        ctx.config,
      )
    }
  } catch (err) {
    console.warn('[formCreateEventRuntime] Failed to parse handler:', err)
    return null
  }
}


return parseFormCreateEventHandler
}
