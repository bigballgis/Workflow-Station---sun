import { FormEventHost } from '@platform-shared/formEvents/host'
import { registerEventApiHost } from '@platform-shared/formEvents/operation'
import type { PortalFormApi } from '@platform-shared/formEvents/api'
import type { PreviewVisibilityBridge } from './formCreatePreviewEvents'

type Model = { get(): Record<string, unknown>; set(value: Record<string, unknown>): void }
const hosts = new WeakMap<object, FormEventHost<{ data: Record<string, unknown>; overlay: unknown }>>()

function executionFor(owner: object, model: Model, bridge?: PreviewVisibilityBridge) {
  const key = bridge?.scopeKey ?? owner
  let host = hosts.get(key)
  if (!host) {
    host = new FormEventHost({
      snapshot: () => ({ data: JSON.parse(JSON.stringify(model.get())) as Record<string, unknown>, overlay: bridge?.snapshot?.() }),
      restore: saved => {
        const preserved = Object.fromEntries(Object.entries(model.get()).filter(([field]) => bridge?.readonlyField?.(field)))
        model.set({ ...saved.data, ...preserved }); bridge?.restore?.(saved.overlay)
      },
      notify: () => bridge?.executionChanged?.(host!.pending, host!.error),
    })
    hosts.set(key, host)
  }
  bridge?.registerExecution?.(host)
  return host
}

export function bindPreviewEventExecution(owner: object, api: PortalFormApi, model: Model, bridge?: PreviewVisibilityBridge, hook = false): PortalFormApi {
  registerEventApiHost(api, (field, value, action) => {
    return executionFor(owner, model, bridge).run(field.startsWith('__') ? '__lifecycle__' : JSON.stringify([field, value]), action, hook ? 'queue' : true)
  })
  return api
}

export function runPreviewFieldExecution(owner: object, api: PortalFormApi, model: Model, bridge: PreviewVisibilityBridge | undefined,
  field: string, value: unknown, action: (api: PortalFormApi) => unknown, programmatic = false) {
  return executionFor(owner, model, bridge).run(JSON.stringify([field, value]), operation => action(operation.bindApi(api)), programmatic ? 'queue' : false)
}
