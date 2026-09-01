import { ref, shallowReactive, type ComputedRef, type Ref } from 'vue'
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
  fields: () => FormField[]
  tabs: () => FormTab[] | undefined
  fieldsAfterTabs: () => FormField[] | undefined
  readonly: () => boolean
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
    focusField: (fieldKey) => focusFormField(deps.formRef, fieldKey),
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

  function createFormEventApi() {
    const resolveFieldKey = createFieldKeyResolver(() => deps.allFields.value)
    return createPortalFormApi(
      () => deps.formData.value,
      (patch) => {
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
    if (!deps.formOptionsOnChange()) return
    runFormOptionsOnChange('__bootstrap__', null)
  }

  function bootstrapComponentHookEvents() {
    if (!deps.formCreateRulesResolved.value.length) return
    const overlays = overlaysApi.buildOverlays()
    runAllComponentHookEvents(
      deps.formCreateRulesResolved.value,
      'load',
      () => deps.formData.value,
      (patch) => { deps.formData.value = { ...deps.formData.value, ...patch } },
      createFieldKeyResolver(() => deps.allFields.value),
      visibilityBag(),
      requiredBag(),
      overlays,
    )
    runAllComponentHookEvents(
      deps.formCreateRulesResolved.value,
      'mounted',
      () => deps.formData.value,
      (patch) => { deps.formData.value = { ...deps.formData.value, ...patch } },
      createFieldKeyResolver(() => deps.allFields.value),
      visibilityBag(),
      requiredBag(),
      overlays,
    )
  }

  return {
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
    scriptLookupFiltersFor: overlaysApi.scriptLookupFiltersFor,
    hasScriptLookupFilter: overlaysApi.hasScriptLookupFilter,
  }
}
