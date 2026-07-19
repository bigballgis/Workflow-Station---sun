<template>
  <div class="form-renderer">
    <el-form
      ref="formRef"
      class="form-readonly-surface"
      :model="formData"
      :rules="formRules"
      :label-width="labelWidth"
      :label-position="labelPosition"
      :hide-required-asterisk="hideRequiredAsterisk"
      :show-message="showValidationMessage"
      :disabled="readonly"
      :size="size"
      :validate-on-rule-change="false"
    >
      <!-- Tab layout: render siblings outside tab panes in designer order -->
      <template v-if="hasTabs">
        <el-row
          v-if="fields.length > 0"
          :gutter="20"
          class="form-fields-before-tabs"
        >
          <FormRendererFields :fields="fields" />
        </el-row>
        <el-tabs
          v-model="activeTab"
          class="form-renderer-tabs"
        >
          <el-tab-pane
            v-for="(tab, tabIdx) in tabs"
            :key="`tab-${tabIdx}-${String(tab.name)}`"
            :label="tab.label"
            :name="tab.name"
          >
            <el-row :gutter="20">
              <FormRendererFields :fields="tab.fields" />
            </el-row>
          </el-tab-pane>
        </el-tabs>
        <el-row
          v-if="fieldsAfterTabs.length > 0"
          :gutter="20"
          class="form-fields-after-tabs"
        >
          <FormRendererFields :fields="fieldsAfterTabs" />
        </el-row>
      </template>

      <!-- Flat layout mode -->
      <template v-else>
        <el-row :gutter="20">
          <FormRendererFields :fields="fields" />
        </el-row>
      </template>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, provide, reactive, toRefs } from 'vue'
import { watchThrottled } from '@vueuse/core'
import { debounce } from 'lodash-es'
import type { FormInstance } from 'element-plus'
import FormRendererFields from './FormRendererFields.vue'
import { FORM_RENDERER_FIELDS_CTX, type FormRendererFieldsContext } from './formRendererFieldsContext'
import { userApi } from '@/api/user'
import type {
  FormField,
  FormTab,
  FormBusinessLogicConfig,
  PortalViewContext,
} from './formRendererHelpers'
import {
  flattenAllFormFieldSegments,
  isFormFieldReadonly,
} from './formRendererHelpers'
import {
  collectFieldComponentEventsFromRules,
} from '@/utils/formCreateComponentEvents'
import { useSubTableBindings, type SubTableBinding } from '@/composables/formRenderer/useSubTableBindings'
import { useSubTablePortalViews } from '@/composables/formRenderer/useSubTablePortalViews'
import { useInlineSubTableForm } from '@/composables/formRenderer/useInlineSubTableForm'
import { useBusinessLogicEngine } from '@/composables/formRenderer/useBusinessLogicEngine'
import { useFormCreateEvents } from '@/composables/formRenderer/useFormCreateEvents'
import { useFormData } from '@/composables/formRenderer/useFormData'
import { useFormValidation } from '@/composables/formRenderer/useFormValidation'
import { useFormAutoSave } from '@/composables/formRenderer/useFormAutoSave'

export type { FormField, FormTab }
export type { SubTableBinding }

console.log(`[PERF-FR] setup start @${performance.now().toFixed(0)}`)

// ---------------------------------------------------------------------------
// Props / Emits
// ---------------------------------------------------------------------------
interface Props {
  fields: FormField[]
  tabs?: FormTab[]
  /** Canvas rules after `el-tabs` (designer siblings below tab widget). */
  fieldsAfterTabs?: FormField[]
  modelValue?: Record<string, any>
  readonly?: boolean
  /** When true, disables form fields driven by PRIMARY table binding READONLY mode.
   *  Does NOT affect sub-table editability (sub-tables use their own bindingMode). */
  primaryReadOnly?: boolean
  labelWidth?: string
  labelPosition?: 'left' | 'right' | 'top'
  size?: 'large' | 'default' | 'small'
  subTableBindings?: SubTableBinding[]
  linkedSubTableBindings?: SubTableBinding[]
  /** MI / diagram preview: row-picking heuristics only; does not override table binding editability. */
  previewSubTables?: boolean
  uploadUrl?: string
  // Task 7.2: BusinessLogicEngine config
  config?: FormBusinessLogicConfig
  // Task 7.5: Auto-save props
  functionUnitId?: string
  formId?: string
  // Task 16: Real-time sync props
  taskId?: string
  /** Current process instance id — enables RecordNote RECORD scope on this form. */
  processInstanceId?: string
  enableSubTablePolling?: boolean
  subTablePollingInterval?: number
  /** When false, hides the sub-table Assign button (only the "Assign Participants" task node allows assignment) */
  allowSubTableAssign?: boolean
  /** In MI todo mode, link-form Details should open blank instead of reusing row-level historical child data. */
  suppressLinkFormInitialData?: boolean
  /** Task To Do only: Link Form field-layout detail shows Cancel/Save (completed / My Request use header close only). */
  showLinkFormDialogFooter?: boolean
  /**
   * Portal view context — drives how subTable nodes are rendered based on their `portalViews` config:
   * - `assigneeTodo`: To Do detail page (办理人待办)
   * - `initiatorRequest`: My Request / process detail page (发起人我的申请)
   * Defaults to `assigneeTodo` for safety; consumers should pass the value matching their route.
   */
  viewContext?: PortalViewContext
  /**
   * When `viewContext` is `initiatorRequest`, Completed Tasks snapshot treats task-status rows
   * like `applicationDetail` (only COMPLETED rows count for Details visibility heuristics).
   */
  initiatorSnapshotMode?: boolean
  /**
   * Current MI participant row id (typically `variables._currentItem.rowId`). When set, the
   * inline form-below-table binds to that row; otherwise it falls back to the first sub-table row.
   */
  currentMiRowId?: number | string | null
  /** Main-table Request ID config — enables live recompute of the readonly __request_id field. */
  requestIdConfig?: { fieldNames: string[]; separator?: string } | null
  /** Binding ids declared on this form's tableBindings (excludes merge-only link targets). */
  nativeSubTableBindingIds?: number[]
  /** Designer configJson — used to resolve link-form targets from {@code subListViews}. */
  formConfig?: Record<string, unknown> | null
  /** PRIMARY table binding metadata (not in subTableBindings list). */
  primaryTableBinding?: {
    tableId?: number | null
    tableName?: string
    fieldDefinitions?: SubTableBinding['fieldDefinitions']
  }
  /** form-create designer options (Form event onChange, labelWidth, etc.). */
  formOptions?: Record<string, unknown> | null
  /** Raw form-create rule tree (for per-component on/_hook events). Falls back to formConfig.rule. */
  formCreateRules?: unknown[] | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  tabs: () => [],
  fieldsAfterTabs: () => [],
  readonly: false,
  primaryReadOnly: false,
  labelWidth: '160px',
  labelPosition: 'left',
  size: 'default',
  subTableBindings: () => [],
  linkedSubTableBindings: undefined,
  previewSubTables: false,
  allowSubTableAssign: true,
  suppressLinkFormInitialData: false,
  showLinkFormDialogFooter: false,
  viewContext: 'assigneeTodo',
  initiatorSnapshotMode: false,
  currentMiRowId: null,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', key: string, value: any): void
  (e: 'update:subTableData', bindingId: number, rows: any[]): void
  /** Form-below-table inline save — same persist path as task action SAVE. */
  (e: 'save'): void
  /** Optional `siblingRows`: that sub-table's row list only (not all bindings). My Request Detail merge uses it. */
  (e: 'viewSubtaskDetail', row: any, siblingRows?: any[]): void
}>()

// ---------------------------------------------------------------------------
// Core refs
// ---------------------------------------------------------------------------
const formRef = ref<FormInstance>()
let isInternalUpdate = false
function setInternalUpdate(v: boolean) { isInternalUpdate = v }

// Department data cache shared via provide/inject (Req 27)
const departmentTreeData = ref<any[]>([])
const departmentTreeLoading = ref(false)
provide('departmentTreeData', departmentTreeData)
provide('departmentTreeLoading', departmentTreeLoading)

const hasTabs = computed(() => props.tabs && props.tabs.length > 0)
const effectiveReadonly = computed(() => props.readonly || props.primaryReadOnly)

function isFieldReadonly(field: FormField): boolean {
  return isFormFieldReadonly(field, effectiveReadonly.value)
}
const activeTab = ref('')

watch(
  () => props.tabs?.map(t => String(t.name)).join('') ?? '',
  () => {
    const newTabs = props.tabs
    if (!newTabs?.length) {
      activeTab.value = ''
      return
    }
    const names = newTabs.map(t => t.name)
    const current = activeTab.value
    const stillValid =
      current !== '' &&
      current !== undefined &&
      names.some(n => String(n) === String(current))
    if (!stillValid) {
      activeTab.value = names[0]!
    }
  },
  { immediate: true },
)

// ---------------------------------------------------------------------------
// Derived field collections / form-create rules
// ---------------------------------------------------------------------------
// Get all fields (including fields in tabs)
const allFields = computed(() =>
  flattenAllFormFieldSegments(props.fields, props.tabs, props.fieldsAfterTabs),
)

const resolvedFormOptionForm = computed(() => {
  const raw = props.formOptions?.form
  return raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : {}
})

/** Designer Preview forces showMessage on; Portal keeps parity unless explicitly disabled. */
const showValidationMessage = computed(() => resolvedFormOptionForm.value.showMessage !== false)
const hideRequiredAsterisk = computed(() => resolvedFormOptionForm.value.hideRequiredAsterisk === true)

const formCreateRulesResolved = computed(() => {
  if (Array.isArray(props.formCreateRules) && props.formCreateRules.length) {
    return props.formCreateRules
  }
  const fromConfig = props.formConfig?.rule
  return Array.isArray(fromConfig) ? fromConfig : []
})

const fieldComponentEvents = computed(() =>
  collectFieldComponentEventsFromRules(formCreateRulesResolved.value),
)

// ---------------------------------------------------------------------------
// Composables — sub-table bindings / portal views / inline form
// ---------------------------------------------------------------------------
const subTableBindingsApi = useSubTableBindings({
  subTableBindings: () => props.subTableBindings,
  linkedSubTableBindings: () => props.linkedSubTableBindings,
  primaryTableBinding: () => props.primaryTableBinding,
  readonly: () => props.readonly,
  allowSubTableAssign: () => props.allowSubTableAssign,
  taskId: () => props.taskId,
  currentMiRowId: () => props.currentMiRowId,
})
const {
  linkableSubTableBindings,
  primaryTableDisplayName,
  primaryTableId,
  parentTablesById,
  subTableBindingsForContext,
  resolveBinding,
  isBindingModeEditable,
  isSubTableEditable,
  subTableAssigneeField,
  resolveMiParticipantSeedForSubTableAdd,
  showSubTableAssignColumn,
} = subTableBindingsApi

const portalViewsApi = useSubTablePortalViews({
  viewContext: () => props.viewContext,
  nativeSubTableBindingIds: () => props.nativeSubTableBindingIds,
  formConfig: () => props.formConfig,
  readonly: () => props.readonly,
  resolveBinding,
  linkableSubTableBindings,
  isBindingModeEditable,
})
const {
  subTableMode,
  shouldRenderPlacedSubTableField,
  subTableCompactLookupCells,
  linkFormScrollToInlineEnabled,
  setSubTableInlineAnchor,
  scrollSubTableInlineIntoView,
  subTableShowTaskStatusInitiator,
  subTableShowViewDetailInitiator,
  resolveInlineFormSourceBinding,
  inlineSubTableFormReadonly,
  resolveInlineFormTableTitle,
  resolveInlineFormFields,
} = portalViewsApi

// ---------------------------------------------------------------------------
// Composable — BusinessLogicEngine (Task 7.2)
// ---------------------------------------------------------------------------
const engineApi = useBusinessLogicEngine({
  config: () => props.config,
  // wrapper closure breaks ordering dependency with useFormData (formData created later)
  formData: { get value() { return formData.value }, set value(v: Record<string, any>) { formData.value = v } } as { value: Record<string, any> },
})
const {
  engine,
  engineVisibility,
  engineOptions,
  engineFieldStates,
  engineCalculatedValues,
  initEngine,
  applyEngineResult,
} = engineApi

// ---------------------------------------------------------------------------
// Composable — form-create Form/Component events + visibility
// ---------------------------------------------------------------------------
const eventsApi = useFormCreateEvents({
  formRef,
  formData: { get value() { return formData.value }, set value(v: Record<string, any>) { formData.value = v } } as { value: Record<string, any> },
  allFields,
  fieldComponentEvents,
  formCreateRulesResolved,
  formOptionsOnChange: () => props.formOptions?.onChange,
  fields: () => props.fields,
  tabs: () => props.tabs,
  fieldsAfterTabs: () => props.fieldsAfterTabs,
  readonly: () => props.readonly,
  engineVisibility,
  emitModelValue: (value) => emit('update:modelValue', value),
})
const {
  isFieldVisible,
  scriptFieldErrors,
  runFormOptionsOnChange,
  runComponentEventsOnFieldChange,
  handleFieldBlur,
  syncDesignerHiddenFieldVisibility,
  bootstrapFormOptionsOnChange,
  bootstrapComponentHookEvents,
} = eventsApi

// ---------------------------------------------------------------------------
// User search — listen to FieldRenderer search:users event (Req 11.2)
// ---------------------------------------------------------------------------
const userSearchResults = ref(new Map<string, Array<{ id: string; name: string }>>())

async function performUserSearch(query: string, fieldKey: string) {
  try {
    const results = await userApi.searchUsers(query)
    userSearchResults.value.set(fieldKey, results)
    userSearchResults.value = new Map(userSearchResults.value)
  } catch {
    userSearchResults.value.set(fieldKey, [])
    userSearchResults.value = new Map(userSearchResults.value)
  }
}

// Debounce per field key: el-select remote-method fires on every keystroke, so
// without this each character would trigger a searchUsers request. One debounced
// fn per field so concurrent user-pickers don't clobber each other's timers.
const userSearchDebouncers = new Map<string, ReturnType<typeof debounce>>()

function handleUserSearch(query: string, fieldKey: string) {
  let fn = userSearchDebouncers.get(fieldKey)
  if (!fn) {
    fn = debounce((q: string) => { void performUserSearch(q, fieldKey) }, 300)
    userSearchDebouncers.set(fieldKey, fn)
  }
  fn(query)
}

// ---------------------------------------------------------------------------
// Composable — form data, rules, field/upload/lookup handlers
// ---------------------------------------------------------------------------
const formDataApi = useFormData({
  formRef,
  allFields,
  modelValue: () => props.modelValue,
  readonly: () => props.readonly,
  config: () => props.config,
  getInternalUpdate: () => isInternalUpdate,
  setInternalUpdate,
  emitChange: (key, value) => emit('change', key, value),
  emitModelValue: (value) => emit('update:modelValue', value),
  emitSubTableData: (bindingId, rows) => emit('update:subTableData', bindingId, rows),
  runComponentEventsOnFieldChange,
  formOptionsOnChange: () => props.formOptions?.onChange,
  fieldComponentEventsHas: (key) => fieldComponentEvents.value.has(key),
  runFormOptionsOnChange,
  engineOnFieldChange: (key, value, fd) => engine.onFieldChange(key, value, fd),
  applyEngineResult,
  engineOnSubTableChange: (bindingId, rows, fd) => engine.onSubTableChange(bindingId, rows, fd),
  engineCalculatedValues,
  requestIdConfig: () => props.requestIdConfig,
})
const {
  formData,
  lookupSelectedData,
  lookupLoadedViewFields,
  lookupShowBackfillView,
  handleLookupSelect,
  handleLookupClear,
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
} = formDataApi

// ---------------------------------------------------------------------------
// Composable — inline sub-table form (MI row picking / update)
// ---------------------------------------------------------------------------
const inlineFormApi = useInlineSubTableForm({
  currentMiRowId: () => props.currentMiRowId,
  suppressLinkFormInitialData: () => props.suppressLinkFormInitialData,
  previewSubTables: () => props.previewSubTables,
  modelValue: () => props.modelValue,
  effectiveReadonly,
  linkableSubTableBindings,
  resolveBinding,
  resolveInlineFormSourceBinding,
  resolveInlineFormFields,
  handleSubTableUpdate,
  emitSave: () => emit('save'),
})
const {
  getCurrentRowForInlineForm,
  handleInlineFormUpdate,
  handleInlineFormSave,
  setInlineFormSelectedRow,
} = inlineFormApi

// ---------------------------------------------------------------------------
// Composable — validation (Task 7.3)
// ---------------------------------------------------------------------------
const { validate } = useFormValidation({
  formRef,
  formData,
  config: () => props.config,
  engine,
  scriptFieldErrors,
})

// ---------------------------------------------------------------------------
// Composable — auto-save to localStorage (Task 7.5)
// ---------------------------------------------------------------------------
const { clearAutoSave, startAutoSave, stopAutoSave, checkAutoSaveRestore } = useFormAutoSave({
  functionUnitId: () => props.functionUnitId,
  formId: () => props.formId,
  readonly: () => props.readonly,
  formData,
  setInternalUpdate,
  emitModelValue: (value) => emit('update:modelValue', value),
  onRestored: (data) => {
    // Trigger engine re-evaluation for all restored fields (Req 12.1, 12.2)
    if (props.config) {
      for (const [key, value] of Object.entries(data)) {
        if (value != null && value !== '') {
          const result = engine.onFieldChange(key, value, formData.value)
          applyEngineResult(result)
        }
      }
    }
  },
})

// ---------------------------------------------------------------------------
// Watchers
// ---------------------------------------------------------------------------
watchThrottled(
  formData,
  (newVal) => {
    if (!isInternalUpdate && !props.readonly) {
      emit('update:modelValue', { ...newVal })
    }
  },
  { throttle: 150 },
)

// Watch modelValue changes — use JSON fingerprint instead of deep watch
// to avoid recursive traversal of the entire modelValue object tree.
let _modelValueFingerprint = ''
watch(() => props.modelValue, (newVal) => {
  // Exclude __subTables__ from comparison — sub-table data updates via
  // patchFormDataSubTablesFromCurrentBindings() should NOT trigger initFormData().
  const { __subTables__: _, ...rest } = newVal || {}
  const fp = JSON.stringify(rest)
  if (fp !== _modelValueFingerprint) {
    _modelValueFingerprint = fp
    initFormData()
  }
})

watch(allFields, (newFields, oldFields) => {
  const hasChanged = newFields.length !== oldFields.length ||
    newFields.some((f, i) => f.key !== oldFields[i]?.key)
  if (hasChanged) {
    initFormData()
  }
})

watch(
  () => props.config,
  () => {
    initEngine()
  },
)

// ---------------------------------------------------------------------------
// provide/inject context for FormRendererFields tree
// ---------------------------------------------------------------------------
// Use toRefs(props) instead of 10 individual computed() wrappers — cheaper to create,
// same reactivity behavior (refs auto-unwrap inside reactive() provide).
const {
  labelWidth: propLabelWidth,
  uploadUrl: propUploadUrl,
  taskId: propTaskId,
  viewContext: propViewContext,
  subTableBindings: propSubTableBindings,
  functionUnitId: propFunctionUnitId,
  processInstanceId: propProcessInstanceId,
  enableSubTablePolling: propEnableSubTablePolling,
  subTablePollingInterval: propSubTablePollingInterval,
  suppressLinkFormInitialData: propSuppressLinkFormInitialData,
  showLinkFormDialogFooter: propShowLinkFormDialogFooter,
} = toRefs(props)

provide(FORM_RENDERER_FIELDS_CTX, reactive({
  formData,
  readonly: effectiveReadonly,
  labelWidth: propLabelWidth,
  uploadUrl: propUploadUrl,
  taskId: propTaskId,
  viewContext: propViewContext,
  subTableBindings: propSubTableBindings,
  linkableSubTableBindings,
  functionUnitId: propFunctionUnitId,
  processInstanceId: propProcessInstanceId,
  resolveMiParticipantSeedForSubTableAdd,
  primaryFormData: formData,
  primaryTableDisplayName,
  primaryTableId,
  parentTablesById,
  subTableBindingsForContext,
  enableSubTablePolling: propEnableSubTablePolling,
  subTablePollingInterval: propSubTablePollingInterval,
  suppressLinkFormInitialData: propSuppressLinkFormInitialData,
  showLinkFormDialogFooter: propShowLinkFormDialogFooter,
  lookupSelectedData,
  lookupLoadedViewFields,
  engineVisibility,
  isFieldVisible,
  engineFieldStates,
  engineOptions,
  userSearchResults,
  isFieldReadonly,
  resolveBinding,
  shouldRenderPlacedSubTableField,
  isSubTableEditable,
  getSubFormRowFormulas,
  getSummaryColumns,
  getSummaryAggregations,
  getSubTableValidation,
  subTableAssigneeField,
  showSubTableAssignColumn,
  linkFormScrollToInlineEnabled,
  subTableShowTaskStatusInitiator,
  subTableShowViewDetailInitiator,
  subTableCompactLookupCells,
  subTableMode,
  resolveInlineFormTableTitle,
  resolveInlineFormFields,
  getCurrentRowForInlineForm,
  inlineSubTableFormReadonly,
  lookupShowBackfillView,
  handleSubTableUpdate,
  handlePrimaryFormDataPatch,
  handleInlineFormSave,
  handleInlineFormUpdate,
  setInlineFormSelectedRow,
  scrollSubTableInlineIntoView,
  setSubTableInlineAnchor,
  handleLookupSelect,
  handleLookupClear,
  handleFieldChange,
  handleFieldBlur,
  scriptFieldErrors,
  handleUploadSuccess,
  handleUploadRemove,
  handleUserSearch,
  emitViewSubtaskDetail: (row: unknown, siblingRows?: unknown[]) => {
    emit('viewSubtaskDetail', row, siblingRows)
  },
}) as unknown as FormRendererFieldsContext)
console.log(`[PERF-FR] setup done (before template) @${performance.now().toFixed(0)}`)

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------
onMounted(() => {
  const _t0 = performance.now()
  console.log(`[PERF-FR] mounted @${_t0.toFixed(0)}`)
  initFormData()
  console.log(`[PERF-FR] initFormData done @${performance.now().toFixed(0)} (+${(performance.now()-_t0).toFixed(0)}ms)`)
  initEngine()
  syncDesignerHiddenFieldVisibility()
  console.log(`[PERF-FR] initEngine done @${performance.now().toFixed(0)} (+${(performance.now()-_t0).toFixed(0)}ms)`)
  bootstrapComponentHookEvents()
  bootstrapFormOptionsOnChange()
  console.log(`[PERF-FR] bootstrap done @${performance.now().toFixed(0)} (+${(performance.now()-_t0).toFixed(0)}ms)`)
  // Task 7.5: Check for auto-saved data, then start auto-save timer
  checkAutoSaveRestore().then(() => {
    console.log(`[PERF-FR] autoSaveRestore done @${performance.now().toFixed(0)} (+${(performance.now()-_t0).toFixed(0)}ms)`)
    startAutoSave()
  })
})

onBeforeUnmount(() => {
  stopAutoSave()
  userSearchDebouncers.forEach(fn => fn.cancel())
  userSearchDebouncers.clear()
})

// ---------------------------------------------------------------------------
// Expose (keep existing + add clearAutoSave)
// ---------------------------------------------------------------------------
defineExpose({
  validate,
  resetForm,
  getFormData,
  setFieldValue,
  clearAutoSave,
  // Exposed for testing (Req 10 property test)
  getSubFormRowFormulas,
  getSummaryColumns,
  getSummaryAggregations,
  getSubTableValidation,
})
</script>

<style scoped lang="scss">
@use '@/styles/form-readonly.scss';

.form-renderer {
  width: 100%;

  :deep(.el-form-item__label) {
    font-weight: 500;
    white-space: nowrap;
    padding-right: 16px;
  }

  /* Form item content in flex layout must be shrinkable and fill remaining width so dropdowns/date pickers render at 100% */
  :deep(.el-form-item__content) {
    flex: 1;
    min-width: 0;
    max-width: 100%;
  }

  :deep(.el-form-item__content .el-select),
  :deep(.el-form-item__content .el-tree-select),
  :deep(.el-form-item__content .el-cascader),
  :deep(.el-form-item__content .el-date-editor) {
    width: 100% !important;
  }

  :deep(.el-form-item__content .el-select .el-select__wrapper),
  :deep(.el-form-item__content .el-tree-select .el-select__wrapper) {
    width: 100%;
  }

  :deep(.el-tabs--border-card) {
    border-radius: 4px;
    width: 100%;

    .el-tabs__header {
      background-color: #f5f7fa;
    }

    .el-tabs__content {
      padding: 20px;
    }
  }

  :deep(.el-form) {
    width: 100%;
  }

  .form-fields-before-tabs,
  .form-fields-after-tabs {
    width: 100%;
    margin-bottom: 18px;
  }

  .form-renderer-tabs {
    width: 100%;
    margin-bottom: 18px;

    :deep(.el-tabs__header) {
      margin-bottom: 0;
    }

    :deep(.el-tabs__content) {
      padding: 16px 0 0;
    }
  }

  .form-renderer-collapse {
    width: 100%;
    margin-bottom: 18px;

    :deep(.el-collapse-item__header) {
      font-weight: 500;
      color: #303133;
    }

    :deep(.el-collapse-item__content) {
      padding: 16px 0 4px;
    }
  }

  .form-layout-card {
    width: 100%;
    margin-bottom: 18px;

    :deep(.el-card__header) {
      padding: 12px 16px;
      font-weight: 500;
      background: #fafafa;
    }
  }

  .form-layout-card-title {
    color: #303133;
  }

  .color-swatch {
    display: inline-block;
    width: 20px;
    height: 20px;
    border-radius: 3px;
    border: 1px solid #dcdfe6;
    vertical-align: middle;
  }

  .editor-readonly {
    padding: 8px;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    background: #f5f7fa;
    min-height: 40px;
    line-height: 1.5;
    word-break: break-word;
    width: 100%;
  }

  .signature-preview {
    max-width: 200px;
    max-height: 80px;
    object-fit: contain;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    background: #fff;
  }

  .lookup-form-item {
    margin-bottom: 18px;

    :deep(.el-form-item__label) {
      display: flex;
      align-items: center;
    }
  }

  .lookup-label-text {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    color: #606266;
  }

  .lookup-label-icon {
    color: #409eff;
    font-size: 14px;
  }

  .lookup-field-wrapper {
    width: 100%;
  }
}
</style>

<style lang="scss">
/* Scoped to .form-renderer container to prevent global style leak (Req 30) */
.form-renderer {
  .form-renderer-popper {
    z-index: 2050;
  }

  :deep(.el-select__popper) {
    z-index: 2050;
  }

  :deep(.el-picker__popper) {
    z-index: 2050;
  }

  :deep(.el-cascader__dropdown) {
    z-index: 2050;
  }

  :deep(.el-tree-select__popper) {
    z-index: 2050;
  }
}
</style>
