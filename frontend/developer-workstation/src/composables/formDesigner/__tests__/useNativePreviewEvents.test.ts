import { describe, expect, it } from 'vitest'
import { useNativePreviewEvents } from '../useNativePreviewEvents'
import type { PortalFormApi } from '@/utils/formCreateEventRuntime'
import { parseFormCreateEventHandler } from '@/utils/formCreateEventRuntime'
import { runNativeFormEvent } from '@platform-shared/formEvents/native'

function fixture(options: Record<string, unknown>) {
  const data: Record<string, unknown> = { source: '', output: '' }
  const rules = Object.fromEntries(Object.keys(data).map(field => [field, { field, type: 'input' }]))
  const native = { fields: () => Object.keys(data), getRule: (field: string) => rules[field],
    getValue: (field: string) => data[field], setValue: (patch: Record<string, unknown>) => Object.assign(data, patch), mergeRule: () => {}, validate: async () => true } as unknown as PortalFormApi
  const events = useNativePreviewEvents(() => options)
  const bound = events.bindOption(options)
  return { data, native, events, bound }
}
describe('native Preview submission contract', () => {
  it('does not drop a real component event after native onChange projects the model', async () => {
    const { data, native, bound, events } = fixture({})
    events.mounted(native)
    data.source = 'selected'
    ;(bound.onChange as Function)('source', 'selected', { api: native })
    await runNativeFormEvent(native, '$FNX:return Promise.resolve().then(() => api.setValue("output", value))', {
      api: native, field: 'source', value: 'selected', formEvent: '__control__', rule: {}, args: ['selected'],
    }, parseFormCreateEventHandler, events.hooks)
    expect(data.output).toBe('selected')
  })
  it('keeps the whole-form readonly guard when a script tries to write a field', async () => {
    const { native, bound, events, data } = fixture({ form: { disabled: true }, onCreated: '$FNX:api.setValue("source", "forbidden")' })
    expect(await (bound.onCreated as (api: PortalFormApi) => unknown)(native)).toBe(false)
    expect(data.source).toBe(''); expect(events.error.value).toBe('subTable.batchEventWriteDenied')
  })
  it('awaits beforeFetch and preserves cyclic config/data references and cancellation', async () => {
    const { native, events, bound } = fixture({ beforeFetch: '$FNX:return Promise.resolve().then(() => { config.headers.marker = "verified"; data.seen = true; return false; });' })
    events.mounted(native)
    const config: { headers: Record<string, string>; self?: unknown } = { headers: {} }; config.self = config
    const data = { seen: false }
    expect(await (bound.beforeFetch as (config: unknown, data: unknown) => unknown)(config, data)).toBe(false)
    expect(config.headers.marker).toBe('verified'); expect(data.seen).toBe(true); expect(config.self).toBe(config)
  })
  it('serializes async lifecycle scripts and awaits onSubmit before collecting the row', async () => {
    const { data, native, bound, events } = fixture({
      onCreated: '$FNX:return Promise.resolve().then(() => api.setValue("output", "created"))',
      onMounted: '$FNX:return Promise.resolve().then(() => api.setValue("output", api.getValue("output") + "|mounted"))',
      onSubmit: '$FNX:return Promise.resolve().then(() => api.setValue("output", api.getValue("output") + "|submitted"))',
    })
    const created = (bound.onCreated as Function)(native)
    const mounted = (bound.onMounted as Function)(native)
    expect(events.pending.value).toBe(true)
    await Promise.all([created, mounted])
    expect(data.output).toBe('created|mounted')
    expect(await events.validate()).toBe(true)
    expect(data.output).toBe('created|mounted|submitted')
  })
  it('preserves false and script field error guards', async () => {
    const options: Record<string, unknown> = { beforeSubmit: '$FNX:return Promise.resolve(false)' }
    const { native, events } = fixture(options)
    events.mounted(native)
    expect(await events.validate()).toBe(false)
    options.beforeSubmit = '$FNX:return Promise.resolve().then(() => api.setFieldError("output", "Missing email"))'
    expect(await events.validate()).toBe(false)
    options.beforeSubmit = '$FNX:api.clearFieldError("output")'
    expect(await events.validate()).toBe(true)
  })
})
