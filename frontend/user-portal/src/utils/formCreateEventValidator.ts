/**
 * Parse and bind designer-stored validator scripts (FORM-CREATE-PREFIX / $FNX:)
 * for Element Plus / async-validator at User Portal runtime.
 */

import {
  containsDangerousFormScript,
  extractFunctionBody,
  isEmptyFormCreateHandler,
  normalizeFunctionSource,
} from './formCreateEventScript'
import {
  createPortalFormApi,
  type FieldKeyResolver,
  type PortalFormApi,
} from './formCreateEventApi'

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
        run: (ctx, _rule, value, callback) => {
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
    // Unified positional signature (this + rest) so per-arity `.call(...)` sites keep a
    // compatible `this`; runtime arg order still matches each compiled arity below.
    type ValidatorRunner = (
      this: { api: PortalFormApi; rule: Record<string, unknown> },
      ...args: unknown[]
    ) => void
    const runner: ValidatorRunner = (() => {
      switch (arity) {
        case 'adapter':
          return new Function('value', 'callback', body) as ValidatorRunner
        case 'valueRuleCallback':
          return new Function('value', 'rule', 'callback', body) as ValidatorRunner
        default:
          return new Function('rule', 'value', 'callback', body) as ValidatorRunner
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
