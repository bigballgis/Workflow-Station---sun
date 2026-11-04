import { ref, shallowRef, onBeforeUnmount, nextTick } from 'vue'
import { runNativeFormEvent } from '@platform-shared/formEvents/native'
import { parseFormCreateEventHandler, isEmptyFormCreateHandler, type PortalFormApi } from '@/utils/formCreateEventRuntime'

/** Native row Preview uses the shared script contract and waits before Save. */
export function useNativePreviewEvents(options: () => Record<string, unknown>, readonly: () => boolean = () => false) {
  const api = shallowRef<PortalFormApi & { validate?(): Promise<unknown> }>()
  const pending = ref(false)
  const error = ref('')
  const hosts = new Set<{ cancel(): void; wait(checkErrors?: boolean): Promise<boolean> }>()
  const cancel = () => { hosts.forEach(host => host.cancel()); hosts.clear() }
  const hooks = {
    readonly: () => readonly() || (options().form as { disabled?: boolean } | undefined)?.disabled === true,
    changed: (running: boolean, failure: unknown) => { pending.value = running; error.value = failure == null ? '' : failure instanceof Error ? failure.message : String(failure) },
    register: (host: { cancel(): void; wait(checkErrors?: boolean): Promise<boolean> }) => { hosts.add(host) },
  }
  onBeforeUnmount(cancel)
  function run(name: string, field = '__form__', value: unknown = null, args: unknown[] = [], extra: { config?: unknown; data?: unknown } = {}) {
    const raw = options()[name]
    if (isEmptyFormCreateHandler(raw) && !['onCreated', 'onMounted', 'onChange'].includes(name)) return
    if (!api.value) throw new Error('Form event API is not ready')
    return runNativeFormEvent(api.value, isEmptyFormCreateHandler(raw) ? '$FNX:;' : raw, { api: api.value, field, value, formEvent: name, rule: {}, args: args.length ? args : [api.value], ...extra }, parseFormCreateEventHandler, hooks)
  }
  const mounted = (native: PortalFormApi) => { api.value = native }
  function bindOption(option: Record<string, unknown>) {
    const bound = { ...option }
    for (const name of ['onCreated', 'onMounted', 'onReload']) bound[name] = (native: PortalFormApi) => { mounted(native); return run(name) }
    bound.onChange = (field: string, value: unknown, inject: { api: PortalFormApi }) => { mounted(inject.api); return run('onChange', field, value, [field, value, inject]) }
    bound.beforeFetch = (config: unknown, data: unknown) => run('beforeFetch', '__fetch__', null, [], { config, data })
    delete bound.beforeSubmit; delete bound.onSubmit; delete bound.onReset
    return bound
  }
  async function wait(checkErrors = true) { for (const host of hosts) if (!await host.wait(checkErrors)) return false; return !error.value }
  async function validate() {
    if (!await wait(false)) return false
    if (await run('beforeSubmit') === false) return false
    if (!await wait()) return false
    if (api.value?.validate && await api.value.validate().catch(() => false) === false) return false
    if (await run('onSubmit') === false || !await wait()) return false
    await nextTick()
    return true
  }
  async function reset() { cancel(); return await run('onReset') !== false }
  return { pending, error, hooks, mounted, bindOption, validate, cancel, reset }
}
