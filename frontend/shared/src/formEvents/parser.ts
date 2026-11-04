import { containsDangerousFormScript, extractFunctionBody, isEmptyFormCreateHandler, normalizeFunctionSource } from './script'
import { createFormEventOptionsBridge, type FormCreateChangeHandler, type FormCreateEventContext, type PortalFormApi } from './api'
import { hostForApi, inFormEventOperation, operationForApi, type FormEventOperation } from './operation'
import { eventSequence } from './async'
import { bindFormEventArgs } from './args'

/** Shared script compiler; identity comes from the hosting application's session. */
export function createFormEventParser(readFormEventCurrentUser: () => unknown) {
function parseFormCreateEventHandler(raw: unknown): ((ctx: FormCreateEventContext) => unknown) | null {
  if (Array.isArray(raw)) {
    const handlers = raw.map(item => parseFormCreateEventHandler(item))
    if (handlers.some(handler => !handler)) return null
    return ctx => eventSequence(handlers.map(handler => () => handler!(ctx)))
  }
  if (typeof raw === 'function') {
    // Prefer tagged $FNX: / FORM-CREATE source so callers can pass a fresh ctx.api
    // (e.g. visibility-wired PortalFormApi) instead of a stale closed-over api.
    // Keep in sync with developer-workstation/src/utils/formCreateEventRuntime.ts
    const tagged = raw as { __hermesFormEventSource?: unknown; __json?: unknown }
    const source = tagged.__hermesFormEventSource ?? tagged.__json
    if ((typeof source === 'string' || Array.isArray(source)) && !isEmptyFormCreateHandler(source)) {
      return parseFormCreateEventHandler(source)
    }
    return (ctx) => invoke(ctx, (api) => (raw as FormCreateChangeHandler)(ctx.field, ctx.value, api))
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
        'fetch',
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
      ) as (inject: Record<string, unknown>, fetch: typeof globalThis.fetch) => unknown
      return (ctx) => {
        const formSnapshot = ctx.api.form
        return invoke(ctx, (api, operation) => runner({
          api,
          rule: ctx.rule,
          self: ctx.rule,
          options: api,
          option: {},
          args: bindArgs(ctx, api),
          field: ctx.field,
          value: ctx.value,
          formData: formSnapshot,
          data: ctx.data === undefined ? formSnapshot : ctx.data,
          config: ctx.config,
          user: readFormEventCurrentUser(),
        }, operation.fetch))
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
      'fetch',
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
      fetch: typeof globalThis.fetch,
    ) => unknown

    return (ctx) => {
      return invoke(ctx, (api, operation) => {
      const options = createFormEventOptionsBridge(api, ctx.rule)
      const formSnapshot = ctx.api.form
      return runner(
        ctx.field,
        ctx.value,
        options,
        api,
        ctx.rule,
        ctx.rule,
        {},
        bindArgs(ctx, api),
        formSnapshot,
        ctx.data === undefined ? formSnapshot : ctx.data,
        readFormEventCurrentUser(),
        ctx.config,
        operation.fetch,
      )
      })
    }
  } catch (err) {
    console.warn('[formCreateEventRuntime] Failed to parse handler:', err)
    return null
  }
}

function invoke(ctx: FormCreateEventContext, action: (api: PortalFormApi, operation: FormEventOperation) => unknown): unknown {
  const existing = operationForApi(ctx.api)
  const run = (operation: FormEventOperation) => operation.invoke(() => action(operation.bindApi(ctx.api), operation))
  if (existing) return run(existing)
  const host = hostForApi(ctx.api)
  return host ? host(ctx.field, ctx.value, run) : inFormEventOperation(run)
}

function bindArgs(ctx: FormCreateEventContext, api: PortalFormApi): unknown {
  return bindFormEventArgs(ctx.args ?? [ctx.value], api, ctx.rule)
}


return parseFormCreateEventHandler
}
