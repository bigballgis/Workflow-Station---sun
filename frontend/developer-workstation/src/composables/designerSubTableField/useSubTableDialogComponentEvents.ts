/**
 * Run Form Design component events inside DW SubTable Add/Edit Preview dialog.
 * Mirrors user-portal/src/composables/subTableAddDialog/useSubTableDialogComponentEvents.ts.
 */
import { ref, shallowReactive, type Ref } from 'vue'
import {
  collectFieldComponentEventsFromRules,
  runComponentFieldEvents,
  runComponentFieldEventsOnValueChange,
} from '@/utils/formCreateComponentEvents'
import {
  createFieldKeyResolver,
  createPortalFormApi,
  type PortalFormRequiredState,
  type PortalFormVisibilityState,
} from '@/utils/formCreateEventRuntime'
import { isEffectivelyDisabled } from '@/utils/formCreateEventOverlays'
import { useFormEventOverlayBags } from '@/composables/formDesigner/useFormEventOverlayBags'

export type DialogColumnWithEvents = {
  field: string
  label?: string
  type?: string
  sourceRule?: Record<string, unknown>
}

export function useSubTableDialogComponentEvents(
  formData: Ref<Record<string, unknown>>,
  getColumns: () => DialogColumnWithEvents[],
) {
  const eventVisibilityState = shallowReactive<PortalFormVisibilityState>({
    hidden: new Map<string, boolean>(),
    display: new Map<string, boolean>(),
  })
  const eventVisibilityTick = ref(0)
  const eventRequiredState = shallowReactive<PortalFormRequiredState>({
    flags: new Map<string, boolean>(),
  })
  const eventRequiredTick = ref(0)
  const scriptFieldErrors = ref<Record<string, string>>({})
  const overlaysApi = useFormEventOverlayBags({
    getAllFieldKeys: () => getColumns().map((c) => c.field),
    getDesignerOptions: (fieldKey) => {
      const opts = getColumns().find((c) => c.field === fieldKey)?.sourceRule?.options
      return Array.isArray(opts) ? opts as { label: string; value: string | number }[] : []
    },
    getDesignerLabel: (fieldKey) => getColumns().find((c) => c.field === fieldKey)?.label ?? fieldKey,
  })

  function notifyEventVisibilityChange() {
    eventVisibilityState.hidden = new Map(eventVisibilityState.hidden)
    eventVisibilityState.display = new Map(eventVisibilityState.display)
    eventVisibilityTick.value++
  }

  function notifyEventRequiredChange() {
    eventRequiredState.flags = new Map(eventRequiredState.flags)
    eventRequiredTick.value++
  }

  function isDialogFieldVisible(fieldKey: string): boolean {
    void eventVisibilityTick.value
    if (eventVisibilityState.hidden.get(fieldKey) === true) return false
    if (eventVisibilityState.display.get(fieldKey) === false) return false
    return true
  }

  function resetDialogEventVisibility() {
    eventVisibilityState.hidden = new Map()
    eventVisibilityState.display = new Map()
    eventVisibilityTick.value++
    eventRequiredState.flags = new Map()
    eventRequiredTick.value++
    scriptFieldErrors.value = {}
    overlaysApi.resetOverlays()
  }

  function createApi() {
    return createPortalFormApi(
      () => formData.value,
      (patch) => {
        formData.value = { ...formData.value, ...patch }
      },
      createFieldKeyResolver(() =>
        getColumns().map((c) => ({ key: c.field, label: c.label })),
      ),
      {
        state: eventVisibilityState,
        notify: notifyEventVisibilityChange,
        getAllFieldKeys: () => getColumns().map((c) => c.field),
      },
      {
        setFieldError: (fieldKey, message) => {
          scriptFieldErrors.value = { ...scriptFieldErrors.value, [fieldKey]: message }
        },
        clearFieldError: (fieldKey) => {
          if (!(fieldKey in scriptFieldErrors.value)) return
          const next = { ...scriptFieldErrors.value }
          delete next[fieldKey]
          scriptFieldErrors.value = next
        },
      },
      {
        state: eventRequiredState,
        notify: notifyEventRequiredChange,
        getAllFieldKeys: () => getColumns().map((c) => c.field),
      },
      overlaysApi.buildOverlays(),
    )
  }

  function resolveEvents(field: string) {
    const col = getColumns().find((c) => c.field === field)
    if (!col?.sourceRule) return undefined
    const ev = collectFieldComponentEventsFromRules([col.sourceRule]).get(field)
    if (!ev) return undefined
    return { col, ev }
  }

  function onDialogFieldChange(field: string, value?: unknown) {
    const found = resolveEvents(field)
    if (!found) return
    if (value !== undefined) {
      formData.value = { ...formData.value, [field]: value }
    }
    const v = value !== undefined ? value : formData.value[field]
    runComponentFieldEventsOnValueChange(found.ev, {
      field,
      value: v,
      api: createApi(),
      onEvent: 'change',
      hookEvent: 'value',
      fieldType: found.col.type,
    })
  }

  function onDialogFieldBlur(field: string) {
    const found = resolveEvents(field)
    if (!found) return
    runComponentFieldEvents(found.ev, {
      field,
      value: formData.value[field],
      api: createApi(),
      onEvent: 'blur',
    })
  }

  function isDialogFieldDisabled(fieldKey: string, fallback: boolean): boolean {
    void overlaysApi.overlayTick.value
    return isEffectivelyDisabled(fieldKey, fallback, overlaysApi.eventDisabledState.flags)
  }

  return {
    onDialogFieldChange,
    onDialogFieldBlur,
    isDialogFieldVisible,
    eventRequiredState,
    eventRequiredTick,
    resetDialogEventVisibility,
    scriptFieldErrors,
    isDialogFieldDisabled,
  }
}
