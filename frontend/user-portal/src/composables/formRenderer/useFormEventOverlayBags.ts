/**
 * Script overlay bags for form events (lock, options, labels, banners, lookup filter, focus).
 * Keep in sync with developer-workstation composables/formDesigner/useFormEventOverlayBags.ts
 */
import { ref, shallowReactive } from 'vue'
import type {
  FormEventChoiceOption,
  FormEventLookupFilter,
  FormEventNotification,
  PortalFormApiOverlays,
  PortalFormDisabledState,
} from '@/utils/formCreateEventOverlays'

export function useFormEventOverlayBags(deps: {
  getAllFieldKeys: () => string[]
  getDesignerOptions: (fieldKey: string) => FormEventChoiceOption[]
  getDesignerLabel: (fieldKey: string) => string
  focusField?: (fieldKey: string) => void
}) {
  const overlayTick = ref(0)
  const eventDisabledState = shallowReactive<PortalFormDisabledState>({
    flags: new Map<string, boolean>(),
  })
  const eventOptionsState = shallowReactive({
    map: new Map<string, FormEventChoiceOption[]>(),
  })
  const eventLabelState = shallowReactive({
    map: new Map<string, string>(),
  })
  const formNotifications = ref<FormEventNotification[]>([])
  const lookupFilterState = shallowReactive({
    map: new Map<string, FormEventLookupFilter[]>(),
  })
  const lookupRefreshNonce = ref<Record<string, number>>({})

  function notifyOverlayChange() {
    overlayTick.value++
  }

  function notifyDisabledChange() {
    eventDisabledState.flags = new Map(eventDisabledState.flags)
    notifyOverlayChange()
  }

  function notifyOptionsChange() {
    eventOptionsState.map = new Map(eventOptionsState.map)
    notifyOverlayChange()
  }

  function notifyLabelChange() {
    eventLabelState.map = new Map(eventLabelState.map)
    notifyOverlayChange()
  }

  function bumpLookupRefresh(fieldKey: string) {
    lookupRefreshNonce.value = {
      ...lookupRefreshNonce.value,
      [fieldKey]: (lookupRefreshNonce.value[fieldKey] ?? 0) + 1,
    }
  }

  function setLookupFilter(fieldKey: string, conditions: FormEventLookupFilter[]) {
    lookupFilterState.map = new Map(lookupFilterState.map)
    lookupFilterState.map.set(fieldKey, conditions)
    bumpLookupRefresh(fieldKey)
    notifyOverlayChange()
  }

  function clearLookupFilter(fieldKey: string) {
    if (!lookupFilterState.map.has(fieldKey)) return
    lookupFilterState.map = new Map(lookupFilterState.map)
    lookupFilterState.map.delete(fieldKey)
    bumpLookupRefresh(fieldKey)
    notifyOverlayChange()
  }

  function buildOverlays(): PortalFormApiOverlays {
    return {
      disabled: {
        state: eventDisabledState,
        notify: notifyDisabledChange,
        getAllFieldKeys: deps.getAllFieldKeys,
      },
      options: {
        get state() {
          return eventOptionsState.map
        },
        notify: notifyOptionsChange,
        getDesignerOptions: deps.getDesignerOptions,
      },
      labels: {
        get state() {
          return eventLabelState.map
        },
        notify: notifyLabelChange,
        getDesignerLabel: deps.getDesignerLabel,
      },
      notifications: {
        set: (item) => {
          formNotifications.value = [
            ...formNotifications.value.filter((n) => n.uniqueId !== item.uniqueId),
            item,
          ]
        },
        clear: (uniqueId) => {
          formNotifications.value = formNotifications.value.filter((n) => n.uniqueId !== uniqueId)
        },
      },
      lookupFilter: {
        set: setLookupFilter,
        clear: clearLookupFilter,
        refresh: bumpLookupRefresh,
      },
      focus: (fieldKey) => deps.focusField?.(fieldKey),
    }
  }

  function resetOverlays() {
    eventDisabledState.flags = new Map()
    eventOptionsState.map = new Map()
    eventLabelState.map = new Map()
    formNotifications.value = []
    lookupFilterState.map = new Map()
    lookupRefreshNonce.value = {}
    overlayTick.value++
  }

  function scriptOptionsFor(fieldKey: string): FormEventChoiceOption[] | undefined {
    void overlayTick.value
    if (!eventOptionsState.map.has(fieldKey)) return undefined
    return eventOptionsState.map.get(fieldKey)
  }

  function scriptLabelOverlay(fieldKey: string): string | undefined {
    void overlayTick.value
    if (!eventLabelState.map.has(fieldKey)) return undefined
    return eventLabelState.map.get(fieldKey)
  }

  function scriptLabelFor(fieldKey: string, fallback: string): string {
    return scriptLabelOverlay(fieldKey) ?? fallback
  }

  function scriptLookupFiltersFor(fieldKey: string): FormEventLookupFilter[] {
    void overlayTick.value
    return lookupFilterState.map.get(fieldKey) ?? []
  }

  function hasScriptLookupFilter(fieldKey: string): boolean {
    return scriptLookupFiltersFor(fieldKey).length > 0
  }

  return {
    overlayTick,
    eventDisabledState,
    formNotifications,
    lookupRefreshNonce,
    buildOverlays,
    resetOverlays,
    scriptOptionsFor,
    scriptLabelFor,
    scriptLabelOverlay,
    scriptLookupFiltersFor,
    hasScriptLookupFilter,
  }
}
