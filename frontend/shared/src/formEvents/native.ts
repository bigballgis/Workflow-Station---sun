import { BatchEventSession, type BatchRule } from '../batch/rowEvents'
import type { FormCreateEventContext, PortalFormApi } from './api'
import { FormEventHost } from './host'
import { afterEvent } from './async'

/** Adapt the native Preview instance through the same guarded session as Batch. */
interface NativeApi {
  fields(): string[]
  getRule(field: string): BatchRule
  getValue(field: string): unknown
  setValue(data: Record<string, unknown>): void
  mergeRule(field: string, patch: Record<string, unknown>): void
}
type Parser = (raw: unknown) => ((context: FormCreateEventContext) => unknown) | null
const controllers = new WeakMap<object, ReturnType<typeof controller>>()

function initialState(native: NativeApi, parse: Parser, readonly?: () => boolean) {
  const keys = native.fields()
  const originals = new Map<string, BatchRule>(keys.map(field => { const rule = native.getRule(field); return [field, { ...rule, props: { ...rule.props }, wrap: { ...rule.wrap } }] }))
  const rules = keys.map(field => {
    const rule = originals.get(field)!
    return { field, type: rule.type, title: typeof rule.title === 'string' ? rule.title : field,
      props: { disabled: rule.disabled === true || rule.props?.disabled === true, readonly: rule.readonly === true || rule.props?.readonly === true },
      on: rule.on, _on: rule._on, hook: rule.hook, _hook: rule._hook, options: rule.options }
  })
  const session = new BatchEventSession(Object.fromEntries(keys.map(field => [field, native.getValue(field)])), rules,
    field => { const rule = rules.find(item => item.field === field); return readonly?.() === true || !rule || rule.props.disabled || rule.props.readonly }, parse)
  return { keys, originals, session }
}

function project(native: NativeApi, displayed: BatchEventSession, originals: Map<string, BatchRule>) {
    native.setValue(displayed.data)
    for (const [field, original] of originals) {
      native.mergeRule(field, {
        info: displayed.infos.has(field) ? { info: displayed.infos.get(field), show: !!displayed.infos.get(field) } : original.info,
        title: displayed.labels.get(field) ?? original.title,
        options: displayed.choices.get(field) ?? original.options,
        hidden: displayed.hidden.get(field) === true, display: displayed.display.get(field) !== false,
        props: { ...original.props, disabled: original.props?.disabled === true || displayed.disabled.get(field) === true },
        wrap: { ...original.wrap, error: displayed.errors.get(field) },
        validate: displayed.required.has(field)
          ? [...(original.validate ?? []).filter((entry: Record<string, unknown>) => !entry.required), ...(displayed.required.get(field) ? [{ required: true }] : [])]
          : original.validate,
      })
    }
}

function controller(native: NativeApi, parse: Parser, notify?: (pending: boolean, error: unknown) => void, readonly?: () => boolean) {
  let syncing = false
  let generation = 0
  let controlKey = ''
  const { keys, originals, session } = initialState(native, parse, readonly)
  let accepted = session
  let displayed = accepted
  const render = () => {
    const version = ++generation
    syncing = true
    project(native, displayed, originals)
    queueMicrotask(() => { if (generation === version) syncing = false })
  }
  const host = new FormEventHost({
    snapshot: () => accepted,
    restore: saved => { accepted = saved; displayed = saved; render() },
    notify: () => { notify?.(host.pending, host.error); render() },
  })
  return {
    cancel: () => host.cancel(), wait: async (checkErrors = true) => await host.wait() && (!checkErrors || displayed.errors.size === 0),
    run(raw: unknown, context: FormCreateEventContext) {
      if (syncing && context.formEvent !== '__control__' && !context.field.startsWith('__')) return
      const key = JSON.stringify([context.field, context.value])
      if (context.formEvent === 'onChange' && host.error != null && controlKey === key) return false
      if (!context.formEvent || context.formEvent === '__control__') controlKey = key
      const values = !host.pending ? JSON.parse(JSON.stringify(Object.fromEntries(keys.map(field => [field, native.getValue(field)])))) as Record<string, unknown> : undefined
      return host.run(key, operation => {
        const working = accepted.fork(operation)
        if (values) working.data = values
        displayed = working
        working.notify = render
        // Native has already accepted the user's direct input; script writes remain guarded.
        if (keys.includes(context.field)) working.data[context.field] = context.value
        const args = Array.isArray(context.args) ? context.args : undefined
        return afterEvent(working.execute(raw, context.field, args, context.rule, context.value, { config: context.config, data: context.data }), value => {
          operation.assertActive()
          accepted = working
          return value
        })
      }, true)
    },
  }
}

export function runNativeFormEvent(api: PortalFormApi, raw: unknown, context: FormCreateEventContext, parse: Parser,
  hooks?: { readonly?(): boolean; changed?(pending: boolean, error: unknown): void; register?(host: { cancel(): void; wait(): Promise<boolean> }): void }): unknown {
  const native = api as unknown as NativeApi
  if (typeof native.fields !== 'function' || typeof native.getRule !== 'function' || typeof native.mergeRule !== 'function') {
    const handler = parse(raw)
    if (!handler) throw new Error('Invalid form event script')
    return handler(context)
  }
  let runtime = controllers.get(native)
  if (!runtime) { runtime = controller(native, parse, hooks?.changed, hooks?.readonly); controllers.set(native, runtime) }
  hooks?.register?.(runtime)
  return runtime.run(raw, context)
}
