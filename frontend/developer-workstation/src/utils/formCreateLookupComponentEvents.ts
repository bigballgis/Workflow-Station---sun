/**
 * fc-designer toolbar Preview: Lookup is a custom form-create component.
 * Built-in preview may strip rule.on / _on on inject.rule — seed a field cache on
 * openPreview prep, then dispatch change/blur handlers via formCreateInject.api.
 */

import {
  collectFieldComponentEventsFromRules,
  runComponentFieldEventsOnValueChange,
  type FieldComponentEvents,
} from '@/utils/formCreateComponentEvents'
import {
  createFormEventOptionsBridge,
  isEmptyFormCreateHandler,
  type PortalFormApi,
} from '@/utils/formCreateEventRuntime'

const fcPreviewEventsByField = new Map<string, FieldComponentEvents>()

function bucketHasHandlers(events: FieldComponentEvents | undefined): boolean {
  if (!events) return false
  const on = events.on ?? {}
  const hook = events.hook ?? {}
  return (
    Object.values(on).some((v) => !isEmptyFormCreateHandler(v))
    || Object.values(hook).some((v) => !isEmptyFormCreateHandler(v))
  )
}

/** Called before fc-designer openPreview (toolbar Preview ②). */
export function seedFcDesignerPreviewComponentEventCache(rules: unknown[]): void {
  fcPreviewEventsByField.clear()
  if (!Array.isArray(rules) || rules.length === 0) return
  for (const [field, ev] of collectFieldComponentEventsFromRules(rules)) {
    if (bucketHasHandlers(ev)) {
      fcPreviewEventsByField.set(field, ev)
    }
  }
}

export function clearFcDesignerPreviewComponentEventCache(): void {
  fcPreviewEventsByField.clear()
}

function resolveFcApi(inject: Record<string, unknown>): PortalFormApi | null {
  const raw = inject.api
  if (!raw || typeof raw !== 'object') return null
  const fcApi = raw as PortalFormApi
  if (typeof fcApi.setValue !== 'function' || typeof fcApi.getValue !== 'function') {
    return null
  }
  return fcApi
}

function resolveFieldEvents(
  field: string,
  rule: Record<string, unknown>,
): FieldComponentEvents | undefined {
  const fromRule = collectFieldComponentEventsFromRules([rule]).get(field)
  if (bucketHasHandlers(fromRule)) return fromRule
  const cached = fcPreviewEventsByField.get(field)
  if (bucketHasHandlers(cached)) return cached
  return fromRule ?? cached
}

/** Run designer change (+ mirrored blur) scripts after lookup selection in fc-designer preview. */
export function dispatchLookupComponentFieldEvents(
  formCreateInject: unknown,
  value: unknown,
): void {
  if (!formCreateInject || typeof formCreateInject !== 'object') return
  const inject = formCreateInject as Record<string, unknown>
  const fcApi = resolveFcApi(inject)
  if (!fcApi) {
    console.warn('[formCreateLookupComponentEvents] missing form-create api for lookup events')
    return
  }

  const rule = (inject.rule && typeof inject.rule === 'object'
    ? inject.rule
    : {}) as Record<string, unknown>
  const field = String(inject.field ?? rule.field ?? '')
  if (!field) return

  const events = resolveFieldEvents(field, rule)
  if (!bucketHasHandlers(events)) return

  const portalApi = createFormEventOptionsBridge(fcApi, rule)
  runComponentFieldEventsOnValueChange(events, {
    field,
    value,
    api: portalApi,
    onEvent: 'change',
    hookEvent: 'value',
    fieldType: 'lookup',
  })
}
