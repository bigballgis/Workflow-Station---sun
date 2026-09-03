<template>
  <div class="form-renderer">
    <el-alert
      v-for="item in formNotifications"
      :key="item.uniqueId"
      class="form-event-banner"
      :title="item.message"
      :type="formNotificationAlertType(item.level)"
      show-icon
      closable
      @close="clearFormNotification(item.uniqueId)"
    />
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
          v-if="renderedFields.length > 0"
          :gutter="20"
          class="form-fields-before-tabs"
        >
          <FormRendererFields :fields="renderedFields" />
        </el-row>
        <el-tabs
          v-model="activeTab"
          class="form-renderer-tabs"
        >
          <el-tab-pane
            v-for="(tab, tabIdx) in renderedTabs"
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
          v-if="renderedFieldsAfterTabs.length > 0"
          :gutter="20"
          class="form-fields-after-tabs"
        >
          <FormRendererFields :fields="renderedFieldsAfterTabs" />
        </el-row>
      </template>

      <!-- Flat layout mode -->
      <template v-else>
        <el-row :gutter="20">
          <FormRendererFields :fields="renderedFields" />
        </el-row>
      </template>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, provide, reactive, toRefs } from 'vue'
import { useI18n } from 'vue-i18n'
import { watchThrottled } from '@vueuse/core'
import { debounce } from 'lodash-es'
import type { FormInstance } from 'element-plus'
import FormRendererFields from './FormRendererFields.vue'
import { FORM_RENDERER_FIELDS_CTX, type FormRendererFieldsContext } from './formRendererFieldsContext'
import { FILE_PREVIEW_PLAYLIST_KEY } from '@/composables/filePreview/useFilePreview'
import { collectFormPreviewFiles } from '@/utils/collectFormPreviewFiles'
import { useAutoOpenFormPreview } from '@/composables/filePreview/useAutoOpenFormPreview'
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
  resolveFormBusinessLogicConfig,
} from './formRendererHelpers'
import {
  collectFieldComponentEventsFromRules,
} from '@/utils/formCreateComponentEvents'
import { applyComputedReadonlyToFormFields } from '@/utils/computedFieldRuntime'
import { useSubTableBindings, type SubTableBinding } from '@/composables/formRenderer/useSubTableBindings'
import { useSubTablePortalViews } from '@/composables/formRenderer/useSubTablePortalViews'
import { useInlineSubFormComponent } from '@/composables/formRenderer/useInlineSubFormComponent'
import { getSavedSubTableRows } from '@/composables/tasks/shared'
import { bindingDeclaresMiParticipantRow } from '@/composables/tasks/miBindingKindFromConfig'
import { useBusinessLogicEngine } from '@/composables/formRenderer/useBusinessLogicEngine'
import { useFormCreateEvents } from '@/composables/formRenderer/useFormCreateEvents'
import { useFormData } from '@/composables/formRenderer/useFormData'
import { isEffectivelyDisabled, isEffectivelyRequired } from '@/utils/formCreateEventRuntime'
import { mergeScriptLookupFilters } from '@/utils/formCreateEventOverlays'
import type { FormEventNotificationLevel } from '@/utils/formCreateEventOverlays'
import { useComputedFields } from '@/composables/formRenderer/useComputedFields'
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
  /**
   * Business-logic JSON (formulas / linkages). Portal pages pass the same object as
   * `formConfig`; if this prop is omitted the engine uses `formConfig`.
   */
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
  /**
   * Task-node field permissions (`TaskFormData.fieldPermissions`). Main-table fields use a bare
   * field-name key; sub-table fields use a composite `${bindingId}:${fieldName}` key. Absent
   * entries default to editable — this only narrows fields explicitly marked READONLY.
   */
  fieldPermissions?: Record<string, string> | null
  /**
   * When true, FormRenderer may auto-open the first previewable upload after data
   * settles, if the account preference is on. Default off. Enable only on the
   * main task / request form — not process-form copies or start drafts.
   */
  autoOpenFilePreview?: boolean
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
  autoOpenFilePreview: false,
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

const { t } = useI18n()

const hasTabs = computed(() => props.tabs && props.tabs.length > 0)
const effectiveReadonly = computed(() => props.readonly || props.primaryReadOnly)
const businessLogicConfig = computed(() =>
  resolveFormBusinessLogicConfig(props.config, props.formConfig),
)

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
// Computed columns are forced read-only here rather than trusted from the form design: the server
// overwrites them on every write regardless of what the designer left editable.
const primaryFieldDefinitions = computed(() => props.primaryTableBinding?.fieldDefinitions)

/**
 * Binding 分类的配置上下文（见 {@code miBindingKindFromConfig}）。
 *
 * <p>collection 的 tableId 从 binding 列表里**读**出来 —— 设计器把 Link Mode 标成
 * "MI Participant Row" 的那个就是；不靠表名/列名猜。拿到它之后，child 与 shared 才能
 * 按「字段级 FK 的 refTableId 指向 collection 还是主表」区分（两者 linkMode 都是 structuralFk）。
 */
const miKindContext = computed(() => {
  const all = [...(props.subTableBindings ?? []), ...(props.linkedSubTableBindings ?? [])]
  const collection = all.find(b => bindingDeclaresMiParticipantRow(b as never))
  return {
    miCollectionTableId: collection?.tableId ?? null,
    primaryTableId: props.primaryTableBinding?.tableId ?? null,
  }
})

const renderedFields = computed(() =>
  applyComputedReadonlyToFormFields(props.fields, primaryFieldDefinitions.value),
)
const renderedFieldsAfterTabs = computed(() =>
  applyComputedReadonlyToFormFields(props.fieldsAfterTabs ?? [], primaryFieldDefinitions.value),
)
const renderedTabs = computed<FormTab[]>(() =>
  (props.tabs ?? []).map(tab => ({
    ...tab,
    fields: applyComputedReadonlyToFormFields(tab.fields, primaryFieldDefinitions.value),
  })),
)

// Get all fields (including fields in tabs)
const allFields = computed(() =>
  flattenAllFormFieldSegments(renderedFields.value, renderedTabs.value, renderedFieldsAfterTabs.value),
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
  resolveBinding,
})
const {
  subTableCompactLookupCells,
  subTableShowTaskStatus,
} = portalViewsApi

// ---------------------------------------------------------------------------
// Composable — BusinessLogicEngine (Task 7.2)
// ---------------------------------------------------------------------------
const engineApi = useBusinessLogicEngine({
  config: () => businessLogicConfig.value,
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
  eventRequiredState,
  eventRequiredTick,
  eventDisabledState,
  overlayTick,
  formNotifications,
  lookupRefreshNonce,
  scriptOptionsFor,
  scriptLabelFor,
  scriptLookupFiltersFor,
  hasScriptLookupFilter,
  clearScriptFieldError,
} = eventsApi

function isFieldRequired(field: FormField): boolean {
  void eventRequiredTick.value
  const fallback = field.required === true
    || engineFieldStates.value.get(field.key)?.required === true
  return isEffectivelyRequired(field.key, fallback, eventRequiredState.flags)
}

function isFieldDisabled(field: FormField): boolean {
  void overlayTick.value
  if (isFieldReadonly(field)) return true
  const engineDisabled = engineFieldStates.value.get(field.key)?.disabled === true
  return isEffectivelyDisabled(field.key, engineDisabled, eventDisabledState.flags)
}

function fieldLabel(field: FormField): string {
  return scriptLabelFor(field.key, field.label)
}

function fieldOptions(field: FormField): unknown {
  const overlay = scriptOptionsFor(field.key)
  if (overlay) return overlay
  return engineOptions.value.get(field.key)
}

function formNotificationAlertType(level: FormEventNotificationLevel): 'error' | 'warning' | 'info' {
  if (level === 'ERROR') return 'error'
  if (level === 'WARNING') return 'warning'
  return 'info'
}

function clearFormNotification(uniqueId: string) {
  formNotifications.value = formNotifications.value.filter((n) => n.uniqueId !== uniqueId)
}

const eventRequiredFlags = computed(() => {
  void eventRequiredTick.value
  return eventRequiredState.flags
})

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
// Composable — computed (formula) columns preview
// ---------------------------------------------------------------------------
const computedFieldsApi = useComputedFields({
  primaryFieldDefinitions: () => primaryFieldDefinitions.value,
  subTableBindings: () => props.subTableBindings,
  // wrapper closure breaks ordering dependency with useFormData (formData created later)
  formData: { get value() { return formData.value }, set value(v: Record<string, any>) { formData.value = v } } as { value: Record<string, any> },
})
const { computedFieldErrors, recomputeComputedFields } = computedFieldsApi

// ---------------------------------------------------------------------------
// Composable — form data, rules, field/upload/lookup handlers
// ---------------------------------------------------------------------------
const formDataApi = useFormData({
  formRef,
  allFields,
  modelValue: () => props.modelValue,
  readonly: () => props.readonly,
  config: () => businessLogicConfig.value,
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
  engineFieldStates,
  eventRequiredFlags,
  requestIdConfig: () => props.requestIdConfig,
  recomputeComputedFields,
})
const {
  formData,
  lookupSelectedData,
  lookupLoadedViewFields,
  lookupShowBackfillView,
  lookupFilterConditionsFor: lookupFilterConditionsForBase,
  handleLookupSelect,
  handleLookupModelUpdate,
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

function lookupFilterConditionsFor(field: FormField) {
  return mergeScriptLookupFilters(
    lookupFilterConditionsForBase(field),
    scriptLookupFiltersFor(field.key),
  )
}

function handleLookupClearAndScriptError(key: string) {
  handleLookupClear(key)
  clearScriptFieldError(key)
}

// ---------------------------------------------------------------------------
// Composable — Inline Form widget (`inlineSubForm`): sub-table form rendered in place
// ---------------------------------------------------------------------------
const {
  resolveInlineSubFormFields,
  resolveInlineSubFormRow,
  resolveInlineSubFormTitle,
  inlineSubFormReadonly,
  handleInlineSubFormUpdate,
} = useInlineSubFormComponent({
  // Matches useSubTableBindings' isSubTableEditable: whole-form readonly (props.readonly) wins,
  // but primaryReadOnly (the PRIMARY table's own bindingMode) must NOT bleed into a sub-table's
  // editability — see inlineSubFormReadonly's own doc comment, which this wiring previously
  // contradicted by using effectiveReadonly (readonly || primaryReadOnly) instead.
  readonly: () => props.readonly,
  resolveBinding,
  isBindingModeEditable,
  getSavedRowsForBinding: (binding) => {
    const st = props.modelValue?.__subTables__
    if (!st || typeof st !== 'object') return undefined
    return getSavedSubTableRows(st as Record<string, unknown>, binding)
  },
  handleSubTableUpdate,
  fieldPermissions: () => props.fieldPermissions,
  currentMiRowId: () => props.currentMiRowId,
  miKindContext: () => miKindContext.value,
})

// ---------------------------------------------------------------------------
// Composable — validation (Task 7.3)
// ---------------------------------------------------------------------------
/**
 * One per-field error surface for the two sources that can produce one. A computed field whose
 * formula fails under onError=fail is included because the server rejects that write, so letting
 * the user submit would only trade an inline message for a failed save.
 */
const fieldErrors = computed<Record<string, string>>(() => {
  const merged = { ...scriptFieldErrors.value }
  for (const [key, code] of Object.entries(computedFieldErrors.value)) {
    merged[key] = t('computedField.evaluationFailed', { code })
  }
  return merged
})

const { validate } = useFormValidation({
  formRef,
  formData,
  config: () => businessLogicConfig.value,
  engine,
  scriptFieldErrors: fieldErrors,
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
    if (businessLogicConfig.value) {
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
  { throttle: 150, deep: true },
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
  businessLogicConfig,
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
  labelPosition: propLabelPosition,
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
  fieldPermissions: propFieldPermissions,
} = toRefs(props)

function collectPreviewFiles() {
  return collectFormPreviewFiles({
    fields: props.fields,
    tabs: props.tabs,
    fieldsAfterTabs: props.fieldsAfterTabs,
    formData: formData.value,
    bindings: [
      ...(props.subTableBindings ?? []),
      ...(linkableSubTableBindings.value ?? []),
    ],
  })
}
provide(FILE_PREVIEW_PLAYLIST_KEY, { collect: collectPreviewFiles })
useAutoOpenFormPreview({
  enabled: () => props.autoOpenFilePreview === true,
  processInstanceId: () => props.processInstanceId,
  collect: collectPreviewFiles,
})

provide(FORM_RENDERER_FIELDS_CTX, reactive({
  formData,
  readonly: effectiveReadonly,
  labelWidth: propLabelWidth,
  labelPosition: propLabelPosition,
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
  isFieldRequired,
  isFieldDisabled,
  fieldLabel,
  fieldOptions,
  engineFieldStates,
  engineOptions,
  userSearchResults,
  isFieldReadonly,
  resolveBinding,
  isSubTableEditable,
  fieldPermissions: propFieldPermissions,
  getSubFormRowFormulas,
  getSummaryColumns,
  getSummaryAggregations,
  getSubTableValidation,
  subTableAssigneeField,
  showSubTableAssignColumn,
  subTableShowTaskStatus,
  subTableCompactLookupCells,
  // Inline Form widget (`inlineSubForm`)
  resolveInlineSubFormFields,
  resolveInlineSubFormRow,
  resolveInlineSubFormTitle,
  inlineSubFormReadonly,
  handleInlineSubFormUpdate,
  lookupShowBackfillView,
  lookupFilterConditionsFor,
  lookupRefreshNonce,
  hasScriptLookupFilter,
  handleSubTableUpdate,
  handlePrimaryFormDataPatch,
  handleLookupSelect,
  handleLookupModelUpdate,
  handleLookupClear: handleLookupClearAndScriptError,
  handleFieldChange,
  handleFieldBlur,
  scriptFieldErrors: fieldErrors,
  clearScriptFieldError,
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
  // Lets the host patch this component's own model (the one getFormData/submit reads) and
  // have derived fields such as __request_id recomputed — writing the parent v-model instead
  // would be dropped, since that copy is only synced back on a throttle.
  handlePrimaryFormDataPatch,
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

  .form-event-banner {
    margin-bottom: 12px;
  }

  :deep(.el-form-item__label) {
    font-weight: 500;
    white-space: nowrap;
    padding-right: 16px;
    height: auto;
    line-height: 1.5;
    padding-top: 6px;
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

  /* FormRendererFields is a fragment; pierce so card chrome stays shared with DW Preview. */
  :deep(.form-layout-card) {
    width: 100%;
    margin-bottom: 10px;

    .el-card__header {
      padding: 12px 16px;
      font-weight: 500;
      background: #fafafa;
    }
  }

  :deep(.form-layout-card-title) {
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
/*
 * FieldRenderer sets popper-class="form-renderer-popper" and teleports to body.
 * Nesting the rule under .form-renderer never matches those nodes, so select /
 * date poppers on form-below-table open under the table stacking context.
 */
.form-renderer-popper {
  z-index: 5000 !important;
}

.form-renderer {
  :deep(.el-select__popper) {
    z-index: 5000;
  }

  :deep(.el-picker__popper) {
    z-index: 5000;
  }

  :deep(.el-cascader__dropdown) {
    z-index: 5000;
  }

  :deep(.el-tree-select__popper) {
    z-index: 5000;
  }
}
</style>
