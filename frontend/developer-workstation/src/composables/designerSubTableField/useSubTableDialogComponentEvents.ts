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
  type PortalFormVisibilityState,
} from '@/utils/formCreateEventRuntime'

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

  function notifyEventVisibilityChange() {
    eventVisibilityState.hidden = new Map(eventVisibilityState.hidden)
    eventVisibilityState.display = new Map(eventVisibilityState.display)
    eventVisibilityTick.value++
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

  return {
    onDialogFieldChange,
    onDialogFieldBlur,
    isDialogFieldVisible,
    resetDialogEventVisibility,
  }
}
