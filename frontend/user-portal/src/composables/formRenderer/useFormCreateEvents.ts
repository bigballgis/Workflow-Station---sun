import { ref, shallowReactive, onBeforeUnmount, nextTick, type ComputedRef, type Ref } from 'vue'
import { FormEventHost } from '@platform-shared/formEvents/host'
import { FormEventOperation } from '@platform-shared/formEvents/operation'
import { eventSequence } from '@platform-shared/formEvents/async'
import type { FormInstance } from 'element-plus'
import type { FormField } from '../../components/formRendererHelpers'
import {
  createPortalFormApi,
  createFieldKeyResolver,
  runFormOnChangeHandler,
  type PortalFormRequiredState,
  type PortalFormVisibilityState,
} from '../../utils/formCreateEventRuntime'
import {
  runAllComponentHookEvents,
  runComponentFieldEvents,
  runComponentFieldEventsOnValueChange,
  type FieldComponentEvents,
} from '../../utils/formCreateComponentEvents'
import { seedDesignerHiddenFieldVisibility } from '../../components/formRendererHelpers'
import type { FormTab } from '../../components/formRendererHelpers'
import { useFormEventOverlayBags } from './useFormEventOverlayBags'

// ---------------------------------------------------------------------------
// form-create Form/Component events + designer-driven visibility
// ---------------------------------------------------------------------------

/** Writable box for formData — satisfied by a Vue Ref or a getter/setter wrapper (TDZ break). */
type FormDataBox = { value: Record<string, any> }

interface FormCreateEventsDeps {
  formRef: Ref<FormInstance | undefined>
  formData: FormDataBox
  allFields: ComputedRef<FormField[]>
  fieldComponentEvents: ComputedRef<Map<string, FieldComponentEvents>>
  formCreateRulesResolved: ComputedRef<unknown[]>
  formOptionsOnChange: () => unknown
  formOptions?: () => Record<string, unknown> | undefined
  fields: () => FormField[]
  tabs: () => FormTab[] | undefined
  fieldsAfterTabs: () => FormField[] | undefined
  readonly: () => boolean
  isFieldReadonly?: (key: string) => boolean
  preserveOnRestore?: () => string[]
  engineVisibility: Ref<Map<string, boolean>>
  emitModelValue: (value: Record<string, any>) => void
}

function collectKeysIncludingLayout(
  fields?: FormField[],
  tabs?: FormTab[],
  after?: FormField[],
): string[] {
  const keys: string[] = []
  const walk = (arr?: FormField[]) => {
    if (!arr) return
    for (const field of arr) {
      if (field.key) keys.push(String(field.key))
      if (field.type === 'tabs' && field.tabs) {
        for (const tab of field.tabs) walk(tab.fields)
      }
      if (field.type === 'collapse' && field.collapsePanels) {
        for (const panel of field.collapsePanels) walk(panel.fields)
      }
      if (field.children?.length) walk(field.children)
    }
  }
  walk(fields)
  for (const tab of tabs || []) walk(tab.fields)
  walk(after)
  return keys
}

function focusFormField(formRef: Ref<FormInstance | undefined>, fieldKey: string): void {
  const root = formRef.value?.$el as HTMLElement | undefined
  if (!root) return
  const item = root.querySelector(`.el-form-item[data-field-key="${CSS.escape(fieldKey)}"]`) as HTMLElement | null
  const target = item?.querySelector('input, textarea, select, button, [tabindex]') as HTMLElement | null
  target?.focus()
}

export function useFormCreateEvents(deps: FormCreateEventsDeps) {
  const engineVisibility = deps.engineVisibility
  const eventVisibilityState = shallowReactive<PortalFormVisibilityState>({
    hidden: new Map<string, boolean>(),
    display: new Map<string, boolean>(),
  })
  const eventVisibilityTick = ref(0)
  const eventRequiredState = shallowReactive<PortalFormRequiredState>({
    flags: new Map<string, boolean>(),
  })
  const eventRequiredTick = ref(0)

  function getAllEventFieldKeys(): string[] {
    const fromTree = collectKeysIncludingLayout(deps.fields(), deps.tabs(), deps.fieldsAfterTabs())
    if (fromTree.length) return fromTree
    return deps.allFields.value.map((f) => f.key)
  }

  const overlaysApi = useFormEventOverlayBags({
    getAllFieldKeys: getAllEventFieldKeys,
    getDesignerOptions: (fieldKey) =>
      deps.allFields.value.find((f) => f.key === fieldKey)?.options ?? [],
    getDesignerLabel: (fieldKey) => {
      const fromLeaf = deps.allFields.value.find((f) => f.key === fieldKey)?.label
      if (fromLeaf != null) return fromLeaf
      return fieldKey
    },
    focusField: (fieldKey) => { void nextTick(() => focusFormField(deps.formRef, fieldKey)) },
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

  function isFieldVisible(fieldKey: string): boolean {
    void eventVisibilityTick.value
    if (eventVisibilityState.hidden.get(fieldKey) === true) return false
    if (eventVisibilityState.display.get(fieldKey) === false) return false
    return engineVisibility.value.get(fieldKey) ?? true
  }

  /** Errors from Form/Component event scripts (`api.setFieldError`). */
  const scriptFieldErrors = ref<Record<string, string>>({})
  const eventPending = ref(false)
  const eventExecutionError = ref('')
  let externalUpdate = false
  const execution = new FormEventHost({
    snapshot: () => ({ data: JSON.parse(JSON.stringify(deps.formData.value)) as Record<string, unknown>,
      hidden: [...eventVisibilityState.hidden], display: [...eventVisibilityState.display], required: [...eventRequiredState.flags],
      errors: { ...scriptFieldErrors.value }, overlays: overlaysApi.snapshotOverlays() }),
    restore: saved => {
      const preserved = Object.fromEntries((deps.preserveOnRestore?.() ?? ['__subTables__']).filter(key => key in deps.formData.value).map(key => [key, deps.formData.value[key]]))
      deps.formData.value = { ...saved.data, ...preserved }
      eventVisibilityState.hidden = new Map(saved.hidden); eventVisibilityState.display = new Map(saved.display)
      eventRequiredState.flags = new Map(saved.required); scriptFieldErrors.value = saved.errors
      overlaysApi.restoreOverlays(saved.overlays); eventVisibilityTick.value++; eventRequiredTick.value++
    },
    notify: () => {
      eventPending.value = execution.pending
      eventExecutionError.value = execution.error instanceof Error ? execution.error.message : execution.error == null ? '' : String(execution.error)
      if (!externalUpdate && !execution.pending && !deps.readonly()) deps.emitModelValue({ ...deps.formData.value })
    },
  })
  onBeforeUnmount(() => execution.cancel())

  function setScriptFieldError(fieldKey: string, message: string) {
    scriptFieldErrors.value = { ...scriptFieldErrors.value, [fieldKey]: message }
  }

  function clearScriptFieldError(fieldKey: string) {
    if (!(fieldKey in scriptFieldErrors.value)) return
    const next = { ...scriptFieldErrors.value }
    delete next[fieldKey]
    scriptFieldErrors.value = next
  }

  function visibilityBag() {
    return {
      state: eventVisibilityState,
      notify: notifyEventVisibilityChange,
      getAllFieldKeys: getAllEventFieldKeys,
    }
  }

  function requiredBag() {
    return {
      state: eventRequiredState,
      notify: notifyEventRequiredChange,
      getAllFieldKeys: getAllEventFieldKeys,
    }
  }

  function createFormEventApi(operation?: FormEventOperation) {
    const resolveFieldKey = createFieldKeyResolver(() => deps.allFields.value)
    const api = createPortalFormApi(
      () => deps.formData.value,
      (patch) => {
        for (const key of Object.keys(patch)) {
          if (!getAllEventFieldKeys().includes(key) || deps.readonly() || deps.isFieldReadonly?.(key)) throw new Error(`Event field write denied: ${key}`)
        }
        deps.formData.value = { ...deps.formData.value, ...patch }
      },
      resolveFieldKey,
      visibilityBag(),
      {
        setFieldError: (fieldKey, message) => {
          setScriptFieldError(fieldKey, message)
        },
        clearFieldError: (fieldKey) => {
          clearScriptFieldError(fieldKey)
        },
      },
      requiredBag(),
      overlaysApi.buildOverlays(),
    )
    return operation ? operation.bindApi(api) : api
  }

  function runFormOptionsOnChange(field: string, value: unknown) {
    const onChangeHandler = deps.formOptionsOnChange()
    if (!onChangeHandler) return
    const rule = deps.fieldComponentEvents.value.get(field)?.rule ?? {}
    return execution.run(field, operation => runFormOnChangeHandler(onChangeHandler, field, value, createFormEventApi(operation), rule), true)
  }

  function runComponentEventsOnFieldChange(key: string, value: unknown, afterScripts?: () => void) {
    execution.cancel()
    deps.formData.value = { ...deps.formData.value, [key]: value }
    const ev = deps.fieldComponentEvents.value.get(key)
    const fieldType = deps.allFields.value.find(f => f.key === key)?.type
    return execution.run(key, operation => eventSequence([() => runComponentFieldEventsOnValueChange(ev, {
      field: key,
      value,
      api: createFormEventApi(operation),
      onEvent: 'change',
      hookEvent: 'value',
      fieldType,
    }), () => runFormOnChangeHandler(deps.formOptionsOnChange(), key, value, createFormEventApi(operation), ev?.rule ?? {}),
    () => afterScripts?.()]))
  }

  /** Component `on.blur` — runs when focus leaves input/textarea (not on each keystroke). */
  function handleFieldBlur(key: string) {
    const value = deps.formData.value[key]
    const ev = deps.fieldComponentEvents.value.get(key)
    execution.run(key, operation => runComponentFieldEvents(ev, {
      field: key,
      value,
      api: createFormEventApi(operation),
      onEvent: 'blur',
    }), true)
    if (!deps.readonly()) {
      void deps.formRef.value?.validateField(key).catch(() => {
        // Expected when validation fails; Element Plus surfaces the field error.
      })
    }
  }

  function syncDesignerHiddenFieldVisibility() {
    eventVisibilityState.hidden = new Map<string, boolean>()
    seedDesignerHiddenFieldVisibility(deps.fields(), deps.tabs(), deps.fieldsAfterTabs(), eventVisibilityState)
    notifyEventVisibilityChange()
  }

  function bootstrapFormOptionsOnChange() {
    if (lifecycleStarted) return
    if (!deps.formOptionsOnChange()) return
    runFormOptionsOnChange('__bootstrap__', null)
  }

  let lifecycleStarted = false
  function bootstrapComponentHookEvents() {
    lifecycleStarted = true
    const options = deps.formOptions?.() ?? {}
    return execution.run('__bootstrap__', operation => {
      const api = createFormEventApi(operation)
      const form = (raw: unknown, field: string) => raw == null ? undefined : runFormOnChangeHandler(raw, field, null, api)
      const hooks = (name: string) => runAllComponentHookEvents(deps.formCreateRulesResolved.value, name,
        () => deps.formData.value, patch => { deps.formData.value = { ...deps.formData.value, ...patch } },
        createFieldKeyResolver(() => deps.allFields.value), visibilityBag(), requiredBag(), overlaysApi.buildOverlays(), api)
      return eventSequence([
        () => form(options.onCreated, '__form__'), () => hooks('load'),
        () => form(deps.formOptionsOnChange(), '__bootstrap__'), () => hooks('mounted'),
        () => form(options.onMounted, '__form__'),
      ])
    })
  }
  function runLifecycle(name: string) {
    const raw = deps.formOptions?.()?.[name]
    if (raw == null) return
    return execution.run(name, operation => runFormOnChangeHandler(raw, '__form__', deps.formData.value, createFormEventApi(operation)))
  }

  return {
    replaceExternalState: (initialize: () => void) => {
      externalUpdate = true
      try { execution.reset(); initialize(); execution.acceptCurrent() } finally { externalUpdate = false }
    },
    eventPending, eventExecutionError,
    waitForEvents: () => execution.wait(),
    cancelEvents: () => execution.cancel(),
    runLifecycle,
    eventVisibilityState,
    eventVisibilityTick,
    notifyEventVisibilityChange,
    eventRequiredState,
    eventRequiredTick,
    isFieldVisible,
    scriptFieldErrors,
    setScriptFieldError,
    clearScriptFieldError,
    createFormEventApi,
    runFormOptionsOnChange,
    runComponentEventsOnFieldChange,
    handleFieldBlur,
    syncDesignerHiddenFieldVisibility,
    bootstrapFormOptionsOnChange,
    bootstrapComponentHookEvents,
    eventDisabledState: overlaysApi.eventDisabledState,
    overlayTick: overlaysApi.overlayTick,
    formNotifications: overlaysApi.formNotifications,
    lookupRefreshNonce: overlaysApi.lookupRefreshNonce,
    scriptOptionsFor: overlaysApi.scriptOptionsFor,
    scriptLabelFor: overlaysApi.scriptLabelFor,
    scriptInfoFor: overlaysApi.scriptInfoFor,
    scriptLookupFiltersFor: overlaysApi.scriptLookupFiltersFor,
    hasScriptLookupFilter: overlaysApi.hasScriptLookupFilter,
  }
}
