import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import { materializeFormCreateValidationRules } from '../../utils/formCreateValidateRules'
import type { FormField, FormBusinessLogicConfig } from '../../components/formRendererHelpers'
import {
  REQUEST_ID_FIELD,
  fieldFeedsRequestId,
  computeRequestId,
  type RequestIdConfig,
} from '../../utils/formFieldMeta'

interface FormDataDeps {
  formRef: Ref<FormInstance | undefined>
  allFields: ComputedRef<FormField[]>
  modelValue: () => Record<string, any>
  readonly: () => boolean
  config: () => FormBusinessLogicConfig | undefined
  getInternalUpdate: () => boolean
  setInternalUpdate: (v: boolean) => void
  emitChange: (key: string, value: any) => void
  emitModelValue: (value: Record<string, any>) => void
  emitSubTableData: (bindingId: number, rows: any[]) => void
  // wired-in cross-composable behavior (broken via closures to avoid TDZ cycles)
  runComponentEventsOnFieldChange: (key: string, value: unknown) => void
  formOptionsOnChange: () => unknown
  fieldComponentEventsHas: (key: string) => boolean
  runFormOptionsOnChange: (field: string, value: unknown) => void
  engineOnFieldChange: (key: string, value: any, formData: Record<string, any>) => any
  applyEngineResult: (result: any) => void
  engineOnSubTableChange: (bindingId: number, rows: any[], formData: Record<string, any>) => any
  engineCalculatedValues: Ref<Map<string, number>>
  /** Main-table Request ID config — drives live recompute of the readonly __request_id field. */
  requestIdConfig?: () => RequestIdConfig | null | undefined
}

export function useFormData(deps: FormDataDeps) {
  const { t } = useI18n()
  const formData = ref<Record<string, any>>({})

  // Lookup selected data state
  const lookupSelectedData = ref<Record<string, Record<string, any>>>({})
  const lookupLoadedViewFields = ref<Record<string, any[]>>({})
  /** Parity with Form Preview / FieldRenderer — honor lookupConfig.showBackfillView === false. */
  function lookupShowBackfillView(field: FormField): boolean {
    return (field as any)._lookupShowBackfillView !== false
  }
  const handleLookupSelect = (fieldKey: string, row: Record<string, any>) => {
    lookupSelectedData.value[fieldKey] = row
  }
  const handleLookupClear = (fieldKey: string) => {
    delete lookupSelectedData.value[fieldKey]
  }

  // Manage file upload lists independently to avoid re-render issues when deriving from formData
  const uploadFileLists = ref<Record<string, Array<{ name: string; url: string; uid?: number }>>>({})

  // ---------------------------------------------------------------------------
  // Form data initialization
  // ---------------------------------------------------------------------------
  const initFormData = () => {
    const data: Record<string, any> = {}
    deps.allFields.value.forEach(field => {
      const bound = deps.modelValue()[field.key]
      if (bound !== undefined && bound !== null && bound !== '') {
        data[field.key] = bound
      } else if (field.defaultValue !== undefined && field.defaultValue !== null && field.defaultValue !== '') {
        data[field.key] = field.defaultValue
      } else if (field.type === 'checkbox') {
        data[field.key] = []
      } else if (field.type === 'switch') {
        data[field.key] = false
      } else {
        data[field.key] = null
      }
    })
    // Seed the readonly Request ID: prefer the backend-filled value, else compute from
    // the contributing fields already present (e.g. new-request page has no backend fill yet).
    const ridCfg = deps.requestIdConfig?.()
    if (ridCfg) {
      const seeded = data[REQUEST_ID_FIELD]
      if (seeded === undefined || seeded === null || seeded === '') {
        const computed = computeRequestId(data, ridCfg)
        if (computed !== undefined) data[REQUEST_ID_FIELD] = computed
      }
    }
    deps.setInternalUpdate(true)
    formData.value = data
    setTimeout(() => { deps.setInternalUpdate(false) }, 0)
    // Element Plus AsyncValidator resolves as micro-tasks after nextTick;
    // use setTimeout (macro-task) to guarantee clearValidate runs last.
    setTimeout(() => {
      const el = deps.formRef.value
      if (el && typeof (el as { clearValidate?: () => void }).clearValidate === 'function') {
        el.clearValidate()
      }
    }, 0)
  }

  // ---------------------------------------------------------------------------
  // Form rules
  // ---------------------------------------------------------------------------
  const formRules = computed<FormRules>(() => {
    if (deps.readonly()) return {}
    const rules: FormRules = {}
    deps.allFields.value.forEach(field => {
      const fieldRules: any[] = []
      if (field.rules?.length) {
        fieldRules.push(
          ...materializeFormCreateValidationRules(
            field.rules,
            () => formData.value,
            () => deps.allFields.value,
          ),
        )
      } else if (field.required) {
        const trigger = field.type === 'select' || field.type === 'checkbox' || field.type === 'switch'
          ? 'change'
          : 'blur'
        if (field.type === 'switch') {
          fieldRules.push({
            type: 'boolean',
            required: true,
            message: t('common.pleaseInput', { label: field.label }),
            trigger,
          })
        } else {
          fieldRules.push({
            required: true,
            message: t('common.pleaseInput', { label: field.label }),
            trigger,
          })
        }
      }
      if (fieldRules.length > 0) {
        rules[field.key] = fieldRules
      }
    })
    return rules
  })

  // ---------------------------------------------------------------------------
  // Field change handler (Task 7.1 + 7.2)
  // ---------------------------------------------------------------------------
  function handleFieldChange(key: string, value: any) {
    formData.value[key] = value

    // Live Request ID: recompute the readonly __request_id when a contributing field changes.
    const ridCfg = deps.requestIdConfig?.()
    if (fieldFeedsRequestId(key, ridCfg)) {
      formData.value[REQUEST_ID_FIELD] = computeRequestId(formData.value, ridCfg) ?? ''
    }

    deps.emitChange(key, value)

    deps.runComponentEventsOnFieldChange(key, value)

    const onChangeHandler = deps.formOptionsOnChange()
    if (onChangeHandler) {
      deps.runFormOptionsOnChange(key, value)
    }
    if (onChangeHandler || deps.fieldComponentEventsHas(key)) {
      if (!deps.readonly()) {
        deps.emitModelValue({ ...formData.value })
      }
    }

    // Task 7.2: Trigger engine evaluation on field change
    if (deps.config()) {
      const result = deps.engineOnFieldChange(key, value, formData.value)
      deps.applyEngineResult(result)
    }
  }

  // ---------------------------------------------------------------------------
  // Upload handlers
  // ---------------------------------------------------------------------------
  function handleUploadSuccess(response: any, _file: any, fieldKey: string) {
    const url = response?.data?.url || ''
    formData.value[fieldKey] = url
    deps.emitModelValue({ ...formData.value })
  }

  function handleUploadRemove(_file: any, fieldKey: string) {
    formData.value[fieldKey] = ''
    deps.emitModelValue({ ...formData.value })
  }

  // ---------------------------------------------------------------------------
  // SubTableField config helpers (Req 10.1, 10.2, 10.3)
  // ---------------------------------------------------------------------------
  function getSubFormRowFormulas(bindingId?: number) {
    const config = deps.config()
    if (!bindingId || !config?.subForms) return undefined
    return config.subForms[String(bindingId)]?.rowFormulas
  }

  function getSummaryColumns(bindingId?: number) {
    const config = deps.config()
    if (!bindingId || !config?.summaryRules) return undefined
    return config.summaryRules
      .filter(r => r.sourceBindingId === bindingId)
      .map(r => r.sourceColumn)
  }

  function getSummaryAggregations(bindingId?: number) {
    const config = deps.config()
    if (!bindingId || !config?.summaryRules) return undefined
    const aggs: Record<string, 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'> = {}
    config.summaryRules
      .filter(r => r.sourceBindingId === bindingId)
      .forEach(r => { aggs[r.sourceColumn] = r.aggregation })
    return Object.keys(aggs).length > 0 ? aggs : undefined
  }

  function getSubTableValidation(bindingId?: number) {
    const config = deps.config()
    if (!bindingId || !config?.subTableValidation) return undefined
    return config.subTableValidation[String(bindingId)]
  }

  function handleSubTableUpdate(bindingId: number, rows: any[]) {
    deps.emitSubTableData(bindingId, rows)

    // Trigger engine summary calculations
    if (deps.config()) {
      const summaryResult = deps.engineOnSubTableChange(bindingId, rows, formData.value)
      for (const [targetField, value] of summaryResult.summaryValues) {
        formData.value[targetField] = value
        deps.engineCalculatedValues.value.set(targetField, value)
      }
      deps.engineCalculatedValues.value = new Map(deps.engineCalculatedValues.value)
    }
  }

  function handlePrimaryFormDataPatch(patch: Record<string, unknown>) {
    if (!patch || typeof patch !== 'object') return
    Object.assign(formData.value, patch)
    // Live Request ID: a programmatic patch (e.g. main PK generated on sub-table add)
    // bypasses handleFieldChange, so recompute here if it touched a contributing field.
    const ridCfg = deps.requestIdConfig?.()
    if (ridCfg && ridCfg.fieldNames.some((name) => Object.prototype.hasOwnProperty.call(patch, name))) {
      formData.value[REQUEST_ID_FIELD] = computeRequestId(formData.value, ridCfg) ?? ''
    }
    deps.emitModelValue({ ...formData.value })
  }

  // ---------------------------------------------------------------------------
  // Existing exposed methods
  // ---------------------------------------------------------------------------
  const resetForm = () => {
    deps.formRef.value?.resetFields()
    initFormData()
  }

  const getFormData = () => {
    return { ...formData.value }
  }

  const setFieldValue = (key: string, value: any) => {
    formData.value[key] = value
  }

  return {
    formData,
    lookupSelectedData,
    lookupLoadedViewFields,
    lookupShowBackfillView,
    handleLookupSelect,
    handleLookupClear,
    uploadFileLists,
    initFormData,
    formRules,
    handleFieldChange,
    handleUploadSuccess,
    handleUploadRemove,
    getSubFormRowFormulas,
    getSummaryColumns,
    getSummaryAggregations,
    getSubTableValidation,
    handleSubTableUpdate,
    handlePrimaryFormDataPatch,
    resetForm,
    getFormData,
    setFieldValue,
  }
}
