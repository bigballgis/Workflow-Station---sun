/**
 * Run Form Design component events (rule.on / rule._hook) inside SubTable Add/Edit dialog.
 * Parity with FormRenderer handleFieldChange — columns carry `sourceRule` from sub-form canvas.
 * Form-level onCreated → onMounted run on dialog open (never replay select change).
 */
import { nextTick, ref, shallowReactive, type Ref } from 'vue'
import {
  collectFieldComponentEventsFromRules,
  runComponentFieldEvents,
  runComponentFieldEventsOnValueChange,
} from '@/utils/formCreateComponentEvents'
import {
  createFieldKeyResolver,
  createPortalFormApi,
  isEmptyFormCreateHandler,
  runFormOnChangeHandler,
  type PortalFormVisibilityState,
} from '@/utils/formCreateEventRuntime'

export type DialogColumnWithEvents = {
  field: string
  label?: string
  type?: string
  /** Placed form-create rule node (on / _on / hook / _hook). */
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

  /** Select / radio / switch / … — also mirrors on.blur for select-like types. */
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

  /** Text / textarea focus leave. */
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

  /**
   * Form Design Form-tab lifecycle: onCreated then onMounted.
   * Does not run component on.change — designers expect change only after user interaction.
   */
  function bootstrapDialogFormLifecycle(formOptions?: Record<string, unknown> | null) {
    if (!formOptions || typeof formOptions !== 'object') return
    const created = formOptions.onCreated
    if (created != null && !isEmptyFormCreateHandler(created)) {
      runFormOnChangeHandler(created, '__form__', null, createApi())
    }
    void nextTick(() => {
      const mounted = formOptions.onMounted
      if (mounted == null || isEmptyFormCreateHandler(mounted)) return
      runFormOnChangeHandler(mounted, '__form__', null, createApi())
    })
  }

  return {
    onDialogFieldChange,
    onDialogFieldBlur,
    isDialogFieldVisible,
    resetDialogEventVisibility,
    bootstrapDialogFormLifecycle,
  }
}
