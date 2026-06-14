import { ref, shallowReactive, type ComputedRef, type Ref } from 'vue'
import type { FormField } from '../../components/formRendererHelpers'
import {
  createPortalFormApi,
  createFieldKeyResolver,
  runFormOnChangeHandler,
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

// ---------------------------------------------------------------------------
// form-create Form/Component events + designer-driven visibility
// ---------------------------------------------------------------------------

/** Writable box for formData — satisfied by a Vue Ref or a getter/setter wrapper (TDZ break). */
type FormDataBox = { value: Record<string, any> }

interface FormCreateEventsDeps {
  formData: FormDataBox
  allFields: ComputedRef<FormField[]>
  fieldComponentEvents: ComputedRef<Map<string, FieldComponentEvents>>
  formCreateRulesResolved: ComputedRef<unknown[]>
  formOptionsOnChange: () => unknown
  fields: () => FormField[]
  tabs: () => FormTab[] | undefined
  fieldsAfterTabs: () => FormField[] | undefined
  readonly: () => boolean
  engineVisibility: Ref<Map<string, boolean>>
  emitModelValue: (value: Record<string, any>) => void
}

export function useFormCreateEvents(deps: FormCreateEventsDeps) {
  const engineVisibility = deps.engineVisibility
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

  function isFieldVisible(fieldKey: string): boolean {
    void eventVisibilityTick.value
    if (eventVisibilityState.hidden.get(fieldKey) === true) return false
    if (eventVisibilityState.display.get(fieldKey) === false) return false
    return engineVisibility.value.get(fieldKey) ?? true
  }

  /** Errors from Form/Component event scripts (`api.setFieldError`). */
  const scriptFieldErrors = ref<Record<string, string>>({})

  function setScriptFieldError(fieldKey: string, message: string) {
    scriptFieldErrors.value = { ...scriptFieldErrors.value, [fieldKey]: message }
  }

  function clearScriptFieldError(fieldKey: string) {
    if (!(fieldKey in scriptFieldErrors.value)) return
    const next = { ...scriptFieldErrors.value }
    delete next[fieldKey]
    scriptFieldErrors.value = next
  }

  function createFormEventApi() {
    const resolveFieldKey = createFieldKeyResolver(() => deps.allFields.value)
    return createPortalFormApi(
      () => deps.formData.value,
      (patch) => {
        deps.formData.value = { ...deps.formData.value, ...patch }
      },
      resolveFieldKey,
      {
        state: eventVisibilityState,
        notify: notifyEventVisibilityChange,
        getAllFieldKeys: () => deps.allFields.value.map(f => f.key),
      },
      {
        setFieldError: (fieldKey, message) => {
          setScriptFieldError(fieldKey, message)
        },
        clearFieldError: (fieldKey) => {
          clearScriptFieldError(fieldKey)
        },
      },
    )
  }

  function runFormOptionsOnChange(field: string, value: unknown) {
    const onChangeHandler = deps.formOptionsOnChange()
    if (!onChangeHandler) return
    const api = createFormEventApi()
    const rule = deps.fieldComponentEvents.value.get(field)?.rule ?? {}
    runFormOnChangeHandler(onChangeHandler, field, value, api, rule)
  }

  function runComponentEventsOnFieldChange(key: string, value: unknown) {
    const api = createFormEventApi()
    const ev = deps.fieldComponentEvents.value.get(key)
    const fieldType = deps.allFields.value.find(f => f.key === key)?.type
    runComponentFieldEventsOnValueChange(ev, {
      field: key,
      value,
      api,
      onEvent: 'change',
      hookEvent: 'value',
      fieldType,
    })
  }

  /** Component `on.blur` — runs when focus leaves input/textarea (not on each keystroke). */
  function handleFieldBlur(key: string) {
    const value = deps.formData.value[key]
    const api = createFormEventApi()
    const ev = deps.fieldComponentEvents.value.get(key)
    runComponentFieldEvents(ev, {
      field: key,
      value,
      api,
      onEvent: 'blur',
    })
    const onChangeHandler = deps.formOptionsOnChange()
    if (onChangeHandler || deps.fieldComponentEvents.value.has(key)) {
      if (!deps.readonly()) {
        deps.emitModelValue({ ...deps.formData.value })
      }
    }
  }

  function syncDesignerHiddenFieldVisibility() {
    eventVisibilityState.hidden = new Map<string, boolean>()
    seedDesignerHiddenFieldVisibility(deps.fields(), deps.tabs(), deps.fieldsAfterTabs(), eventVisibilityState)
    notifyEventVisibilityChange()
  }

  function bootstrapFormOptionsOnChange() {
    if (!deps.formOptionsOnChange()) return
    runFormOptionsOnChange('__bootstrap__', null)
  }

  function bootstrapComponentHookEvents() {
    if (!deps.formCreateRulesResolved.value.length) return
    runAllComponentHookEvents(
      deps.formCreateRulesResolved.value,
      'load',
      () => deps.formData.value,
      (patch) => { deps.formData.value = { ...deps.formData.value, ...patch } },
      createFieldKeyResolver(() => deps.allFields.value),
      {
        state: eventVisibilityState,
        notify: notifyEventVisibilityChange,
        getAllFieldKeys: () => deps.allFields.value.map(f => f.key),
      },
    )
    runAllComponentHookEvents(
      deps.formCreateRulesResolved.value,
      'mounted',
      () => deps.formData.value,
      (patch) => { deps.formData.value = { ...deps.formData.value, ...patch } },
      createFieldKeyResolver(() => deps.allFields.value),
      {
        state: eventVisibilityState,
        notify: notifyEventVisibilityChange,
        getAllFieldKeys: () => deps.allFields.value.map(f => f.key),
      },
    )
  }

  return {
    eventVisibilityState,
    eventVisibilityTick,
    notifyEventVisibilityChange,
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
  }
}
