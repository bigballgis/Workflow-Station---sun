/**
 * Run Form Design component events (rule.on / rule._hook) inside SubTable Add/Edit dialog.
 * Parity with FormRenderer handleFieldChange — columns carry `sourceRule` from sub-form canvas.
 *
 * Dialog open replays the same bootstrap FormRenderer.onMounted does (see FormRenderer.vue:
 * syncDesignerHiddenFieldVisibility → bootstrapComponentHookEvents → bootstrapFormOptionsOnChange),
 * plus the Form-level onCreated/onMounted this dialog owns. Never replays component on.change:
 * designers expect change only after user interaction.
 */
import { nextTick, ref, shallowReactive, type Ref } from 'vue'
import {
  collectFieldComponentEventsFromRules,
  runAllComponentHookEvents,
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
  /** Designer "Hide" toggle carried over from the sub-form canvas rule. */
  hidden?: boolean
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
   * Seed visibility from the designer "Hide" toggle, mirroring FormRenderer's
   * syncDesignerHiddenFieldVisibility. Runs before any script so `api.hidden(false, …)`
   * in onCreated / hooks / onChange can still reveal a statically hidden field.
   */
  function seedDialogStaticHiddenVisibility() {
    for (const col of getColumns()) {
      if (col.hidden === true && col.field) eventVisibilityState.hidden.set(col.field, true)
    }
    notifyEventVisibilityChange()
  }

  /** Component-level `_hook` phases — the dialog's equivalent of bootstrapComponentHookEvents. */
  function runDialogComponentHooks(hookName: 'load' | 'mounted') {
    const rules = getColumns()
      .map((c) => c.sourceRule)
      .filter((r): r is Record<string, unknown> => r != null)
    if (!rules.length) return
    runAllComponentHookEvents(
      rules,
      hookName,
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

  function runDialogFormHandler(raw: unknown, field: string) {
    if (raw == null || isEmptyFormCreateHandler(raw)) return
    runFormOnChangeHandler(raw, field, null, createApi())
  }

  /**
   * Dialog-open bootstrap, in form-create lifecycle order:
   *   designer Hide seed → Form onCreated → component hook `load` → Form onChange('__bootstrap__')
   *   → (nextTick) component hook `mounted` → Form onMounted
   *
   * The onChange leg matches FormRenderer.bootstrapFormOptionsOnChange: sub-forms that put
   * their init logic in the Form-tab onChange (guarded on `field === '__bootstrap__'`) got
   * nothing here before, so statically hidden fields stayed visible on first open.
   * onMounted stays deferred — the dialog body has not rendered when this watcher fires.
   */
  function bootstrapDialogFormLifecycle(formOptions?: Record<string, unknown> | null) {
    seedDialogStaticHiddenVisibility()
    const options = formOptions && typeof formOptions === 'object' ? formOptions : null
    if (options) runDialogFormHandler(options.onCreated, '__form__')
    runDialogComponentHooks('load')
    if (options) runDialogFormHandler(options.onChange, '__bootstrap__')
    void nextTick(() => {
      runDialogComponentHooks('mounted')
      if (options) runDialogFormHandler(options.onMounted, '__form__')
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
