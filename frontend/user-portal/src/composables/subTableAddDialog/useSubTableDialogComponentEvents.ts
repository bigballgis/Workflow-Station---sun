/**
 * Run Form Design component events (rule.on / rule._hook) and Form-level options.*
 * inside SubTable Add/Edit dialog — Preview (form-create) parity.
 *
 * Dialog open bootstrap (form-create order):
 *   designer Hide seed → Form onCreated → component hook `load` → Form onChange('__bootstrap__')
 *   → (nextTick) component hook `mounted` → Form onMounted
 *
 * Field edits also run Form onChange (not only component change). Save/close run
 * beforeSubmit / onSubmit / onReset. Re-init while open runs onReload.
 * beforeFetch: N/A — dialog has no form-create remote-fetch pipeline.
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
  parseFormCreateEventHandler,
  runFormOnChangeHandler,
  type PortalFormRequiredState,
  type PortalFormVisibilityState,
} from '@/utils/formCreateEventRuntime'
import { useFormEventOverlayBags } from '@/composables/formRenderer/useFormEventOverlayBags'
import { isEffectivelyDisabled } from '@/utils/formCreateEventOverlays'

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
  getFormOptions: () => Record<string, unknown> | null | undefined = () => null,
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
      const rule = getColumns().find((c) => c.field === fieldKey)?.sourceRule
      const opts = rule?.options
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

  function resolveFormOptions(
    override?: Record<string, unknown> | null,
  ): Record<string, unknown> | null {
    if (override && typeof override === 'object') return override
    const opts = getFormOptions()
    return opts && typeof opts === 'object' ? opts : null
  }

  function runDialogFormHandler(raw: unknown, field: string, value: unknown = null): unknown {
    if (raw == null || isEmptyFormCreateHandler(raw)) return undefined
    return runFormOnChangeHandler(raw, field, value, createApi())
  }

  function runFormOptionsOnChange(field: string, value: unknown) {
    const options = resolveFormOptions()
    if (!options) return
    runDialogFormHandler(options.onChange, field, value)
  }

  function resolveEvents(field: string) {
    const col = getColumns().find((c) => c.field === field)
    if (!col?.sourceRule) return undefined
    const ev = collectFieldComponentEventsFromRules([col.sourceRule]).get(field)
    if (!ev) return undefined
    return { col, ev }
  }

  /**
   * Select / radio / switch / … — component change (+ mirrored blur for select-like),
   * then Form-level onChange (Preview / FormRenderer parity). Runs Form onChange even
   * when the column has no sourceRule — v-model may already own the value.
   */
  function onDialogFieldChange(field: string, value?: unknown) {
    if (value !== undefined) {
      formData.value = { ...formData.value, [field]: value }
    }
    const v = value !== undefined ? value : formData.value[field]
    const found = resolveEvents(field)
    if (found) {
      runComponentFieldEventsOnValueChange(found.ev, {
        field,
        value: v,
        api: createApi(),
        onEvent: 'change',
        hookEvent: 'value',
        fieldType: found.col.type,
      })
    }
    runFormOptionsOnChange(field, v)
  }

  /** Text / textarea focus leave — component on.blur only (FormRenderer parity). */
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
      {
        state: eventRequiredState,
        notify: notifyEventRequiredChange,
        getAllFieldKeys: () => getColumns().map((c) => c.field),
      },
      overlaysApi.buildOverlays(),
    )
  }

  /**
   * Dialog-open bootstrap, in form-create lifecycle order:
   *   designer Hide seed → Form onCreated → component hook `load` → Form onChange('__bootstrap__')
   *   → (nextTick) component hook `mounted` → Form onMounted
   */
  function bootstrapDialogFormLifecycle(formOptions?: Record<string, unknown> | null) {
    seedDialogStaticHiddenVisibility()
    const options = resolveFormOptions(formOptions)
    if (options) runDialogFormHandler(options.onCreated, '__form__')
    runDialogComponentHooks('load')
    if (options) runDialogFormHandler(options.onChange, '__bootstrap__')
    void nextTick(() => {
      runDialogComponentHooks('mounted')
      if (options) runDialogFormHandler(options.onMounted, '__form__')
    })
  }

  /** Form onReload — when dialog re-inits while still open (mode/initialData swap). */
  function runFormOnReload(formOptions?: Record<string, unknown> | null) {
    const options = resolveFormOptions(formOptions)
    if (!options) return
    runDialogFormHandler(options.onReload, '__form__')
  }

  /**
   * Form beforeSubmit — return false to abort save (form-create parity).
   * Empty / missing handler → allow save.
   * Parse failure or thrown error → abort (fail-closed); do not reuse
   * runFormOnChangeHandler which swallows exceptions for UX scripts.
   */
  function runFormBeforeSubmit(formOptions?: Record<string, unknown> | null): boolean {
    const options = resolveFormOptions(formOptions)
    if (!options || isEmptyFormCreateHandler(options.beforeSubmit)) return true
    try {
      const handler = parseFormCreateEventHandler(options.beforeSubmit)
      if (!handler) {
        console.warn('[useSubTableDialogComponentEvents] beforeSubmit parse failed; aborting save')
        return false
      }
      const result = handler({
        field: '__submit__',
        value: formData.value,
        api: createApi(),
        rule: {},
      })
      return result !== false
    } catch (err) {
      console.warn('[useSubTableDialogComponentEvents] beforeSubmit error; aborting save:', err)
      return false
    }
  }

  /** Form onSubmit — after validation, before persist emit. */
  function runFormOnSubmit(formOptions?: Record<string, unknown> | null) {
    const options = resolveFormOptions(formOptions)
    if (!options) return
    runDialogFormHandler(options.onSubmit, '__submit__', formData.value)
  }

  /** Form onReset — when dialog closes / model cleared. */
  function runFormOnReset(formOptions?: Record<string, unknown> | null) {
    const options = resolveFormOptions(formOptions)
    if (!options) return
    runDialogFormHandler(options.onReset, '__form__')
  }

  return {
    onDialogFieldChange,
    onDialogFieldBlur,
    isDialogFieldVisible,
    eventRequiredState,
    eventRequiredTick,
    resetDialogEventVisibility,
    bootstrapDialogFormLifecycle,
    runFormOnReload,
    runFormBeforeSubmit,
    runFormOnSubmit,
    runFormOnReset,
    scriptFieldErrors,
    overlayTick: overlaysApi.overlayTick,
    eventDisabledState: overlaysApi.eventDisabledState,
    isDialogFieldDisabled: (fieldKey: string, fallback: boolean) => {
      void overlaysApi.overlayTick.value
      return isEffectivelyDisabled(fieldKey, fallback, overlaysApi.eventDisabledState.flags)
    },
  }
}
