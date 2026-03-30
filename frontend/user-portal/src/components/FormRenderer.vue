<template>
  <div class="form-renderer">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      :label-width="labelWidth"
      :label-position="labelPosition"
      :disabled="readonly"
      :size="size"
    >
      <!-- Tab 布局模式 -->
      <template v-if="hasTabs">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane
            v-for="tab in tabs"
            :key="tab.name"
            :label="tab.label"
            :name="tab.name"
          >
            <el-row :gutter="20">
              <template v-for="field in tab.fields" :key="field.key">
                <template v-if="field.type === 'subTable'">
                  <el-col :span="24" style="padding: 0;">
                    <SubTableField
                      v-if="resolveBinding(field._bindingId)"
                      :title="resolveBinding(field._bindingId)!.tableName"
                      :columns="resolveBinding(field._bindingId)!.columns"
                      :model-value="resolveBinding(field._bindingId)!.data"
                      :editable="!readonly && resolveBinding(field._bindingId)!.bindingMode === 'EDITABLE'"
                      :row-formulas="getSubFormRowFormulas(field._bindingId)"
                      :summary-columns="getSummaryColumns(field._bindingId)"
                      :summary-aggregations="getSummaryAggregations(field._bindingId)"
                      :validation-config="getSubTableValidation(field._bindingId)"
                      :upload-url="uploadUrl"
                      @update:model-value="(rows: any[]) => handleSubTableUpdate(field._bindingId!, rows)"
                      style="margin-bottom: 16px;"
                    />
                  </el-col>
                </template>
                <el-col v-else :span="field.span || 24" v-show="engineVisibility.get(field.key) ?? true">
                  <el-form-item
                    :label="field.label"
                    :prop="field.key"
                    :required="field.required"
                  >
                    <FieldRenderer
                      :field="field"
                      :model-value="formData[field.key]"
                      :readonly="readonly"
                      :disabled="engineFieldStates.get(field.key)?.disabled || false"
                      :visible="engineVisibility.get(field.key) ?? true"
                      :options="engineOptions.get(field.key)"
                      :upload-url="uploadUrl"
                      :user-search-results="userSearchResults.get(field.key)"
                      @update:model-value="(val: any) => handleFieldChange(field.key, val)"
                      @upload:success="(res: any, file: any, key: string) => handleUploadSuccess(res, file, key)"
                      @upload:remove="(file: any, key: string) => handleUploadRemove(file, key)"
                      @search:users="handleUserSearch"
                    />
                  </el-form-item>
                </el-col>
              </template>
            </el-row>
          </el-tab-pane>
        </el-tabs>
      </template>

      <!-- 普通平铺模式 -->
      <template v-else>
        <el-row :gutter="20">
          <template v-for="field in fields" :key="field.key">
            <template v-if="field.type === 'subTable'">
              <el-col :span="24" style="padding: 0;">
                <SubTableField
                  v-if="resolveBinding(field._bindingId)"
                  :title="resolveBinding(field._bindingId)!.tableName"
                  :columns="resolveBinding(field._bindingId)!.columns"
                  :model-value="resolveBinding(field._bindingId)!.data"
                  :editable="!readonly && resolveBinding(field._bindingId)!.bindingMode === 'EDITABLE'"
                  :row-formulas="getSubFormRowFormulas(field._bindingId)"
                  :summary-columns="getSummaryColumns(field._bindingId)"
                  :summary-aggregations="getSummaryAggregations(field._bindingId)"
                  :validation-config="getSubTableValidation(field._bindingId)"
                  :upload-url="uploadUrl"
                  @update:model-value="(rows: any[]) => handleSubTableUpdate(field._bindingId!, rows)"
                  style="margin-bottom: 16px;"
                />
              </el-col>
            </template>
            <el-col v-else :span="field.span || 24" v-show="engineVisibility.get(field.key) ?? true">
              <el-form-item
                :label="field.label"
                :prop="field.key"
                :required="field.required"
              >
                <FieldRenderer
                  :field="field"
                  :model-value="formData[field.key]"
                  :readonly="readonly"
                  :disabled="engineFieldStates.get(field.key)?.disabled || false"
                  :visible="engineVisibility.get(field.key) ?? true"
                  :options="engineOptions.get(field.key)"
                  :upload-url="uploadUrl"
                  :user-search-results="userSearchResults.get(field.key)"
                  @update:model-value="(val: any) => handleFieldChange(field.key, val)"
                  @upload:success="(res: any, file: any, key: string) => handleUploadSuccess(res, file, key)"
                  @upload:remove="(file: any, key: string) => handleUploadRemove(file, key)"
                  @search:users="handleUserSearch"
                />
              </el-form-item>
            </el-col>
          </template>
        </el-row>
      </template>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, provide } from 'vue'
import { useI18n } from 'vue-i18n'
import { isEqual } from 'lodash-es'
import { ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import SubTableField from './SubTableField.vue'
import FieldRenderer from './FieldRenderer.vue'
import { BusinessLogicEngine } from './businessLogicEngine'
import { userApi } from '@/api/user'
import type { FormField, FormTab, FormBusinessLogicConfig } from './formRendererHelpers'

export type { FormField, FormTab }

const { t } = useI18n()

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------
interface SubTableBinding {
  bindingId: number
  bindingType: string
  bindingMode: string
  tableName: string
  tableType: string
  tableDescription: string
  columns: any[]
  data: any[]
}

interface Props {
  fields: FormField[]
  tabs?: FormTab[]
  modelValue?: Record<string, any>
  readonly?: boolean
  labelWidth?: string
  labelPosition?: 'left' | 'right' | 'top'
  size?: 'large' | 'default' | 'small'
  subTableBindings?: SubTableBinding[]
  uploadUrl?: string
  // Task 7.2: BusinessLogicEngine config
  config?: FormBusinessLogicConfig
  // Task 7.5: Auto-save props
  functionUnitId?: string
  formId?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  tabs: () => [],
  readonly: false,
  labelWidth: '160px',
  labelPosition: 'left',
  size: 'default',
  subTableBindings: () => [],
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', key: string, value: any): void
  (e: 'update:subTableData', bindingId: number, rows: any[]): void
}>()

// ---------------------------------------------------------------------------
// Core refs
// ---------------------------------------------------------------------------
const formRef = ref<FormInstance>()
const formData = ref<Record<string, any>>({})
let isInternalUpdate = false

// Department data cache shared via provide/inject (Req 27)
const departmentTreeData = ref<any[]>([])
const departmentTreeLoading = ref(false)
provide('departmentTreeData', departmentTreeData)
provide('departmentTreeLoading', departmentTreeLoading)

const hasTabs = computed(() => props.tabs && props.tabs.length > 0)
const activeTab = ref('')

watch(() => props.tabs, (newTabs) => {
  if (newTabs && newTabs.length > 0 && !activeTab.value) {
    activeTab.value = newTabs[0].name
  }
}, { immediate: true })

const bindingMap = computed(() => {
  const map = new Map<number, SubTableBinding>()
  for (const b of (props.subTableBindings ?? [])) map.set(b.bindingId, b)
  return map
})
const resolveBinding = (id?: number) => id != null ? bindingMap.value.get(id) : undefined

const allFields = computed(() => {
  if (hasTabs.value && props.tabs) {
    return props.tabs.flatMap(tab => tab.fields)
  }
  return props.fields
})

// ---------------------------------------------------------------------------
// Task 7.2: BusinessLogicEngine integration
// ---------------------------------------------------------------------------
const engine = new BusinessLogicEngine()
const engineVisibility = ref(new Map<string, boolean>())
const engineOptions = ref(new Map<string, Array<{ label: string; value: any }>>())
const engineFieldStates = ref(new Map<string, { disabled?: boolean; required?: boolean }>())
const engineCalculatedValues = ref(new Map<string, number>())

function initEngine() {
  if (props.config) {
    engine.init(props.config)
  }
}

// ---------------------------------------------------------------------------
// User search — listen to FieldRenderer search:users event (Req 11.2)
// ---------------------------------------------------------------------------
const userSearchResults = ref(new Map<string, Array<{ id: string; name: string }>>())

async function handleUserSearch(query: string, fieldKey: string) {
  try {
    const results = await userApi.searchUsers(query)
    userSearchResults.value.set(fieldKey, results)
    userSearchResults.value = new Map(userSearchResults.value)
  } catch {
    userSearchResults.value.set(fieldKey, [])
    userSearchResults.value = new Map(userSearchResults.value)
  }
}

function applyEngineResult(result: {
  visibilityChanges: Map<string, boolean>
  calculatedValues: Map<string, number>
  optionChanges: Map<string, Array<{ label: string; value: any }>>
  stateChanges: Map<string, { disabled?: boolean; required?: boolean }>
}) {
  // Merge visibility changes
  for (const [k, v] of result.visibilityChanges) {
    engineVisibility.value.set(k, v)
  }
  // Merge calculated values and update formData
  for (const [k, v] of result.calculatedValues) {
    engineCalculatedValues.value.set(k, v)
    formData.value[k] = v
  }
  // Merge option changes
  for (const [k, v] of result.optionChanges) {
    engineOptions.value.set(k, v)
  }
  // Merge state changes
  for (const [k, v] of result.stateChanges) {
    engineFieldStates.value.set(k, v)
  }
  // Trigger reactivity
  engineVisibility.value = new Map(engineVisibility.value)
  engineOptions.value = new Map(engineOptions.value)
  engineFieldStates.value = new Map(engineFieldStates.value)
  engineCalculatedValues.value = new Map(engineCalculatedValues.value)
}

// ---------------------------------------------------------------------------
// Form data initialization
// ---------------------------------------------------------------------------
const initFormData = () => {
  const data: Record<string, any> = {}
  allFields.value.forEach(field => {
    if (props.modelValue[field.key] !== undefined) {
      data[field.key] = props.modelValue[field.key]
    } else if (field.defaultValue !== undefined) {
      data[field.key] = field.defaultValue
    } else if (field.type === 'checkbox') {
      data[field.key] = []
    } else {
      data[field.key] = null
    }
  })
  isInternalUpdate = true
  formData.value = data
  setTimeout(() => { isInternalUpdate = false }, 0)
}

// ---------------------------------------------------------------------------
// Form rules
// ---------------------------------------------------------------------------
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  allFields.value.forEach(field => {
    if (field.required || field.rules) {
      const fieldRules: any[] = []
      if (field.required) {
        fieldRules.push({
          required: true,
          message: t('common.pleaseInput', { label: field.label }),
          trigger: field.type === 'select' ? 'change' : 'blur'
        })
      }
      if (field.rules) {
        fieldRules.push(...field.rules)
      }
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
  emit('change', key, value)

  // Task 7.2: Trigger engine evaluation on field change
  if (props.config) {
    const result = engine.onFieldChange(key, value, formData.value)
    applyEngineResult(result)
  }
}

// ---------------------------------------------------------------------------
// Upload handlers
// ---------------------------------------------------------------------------
function handleUploadSuccess(response: any, _file: any, fieldKey: string) {
  const url = response?.data?.url || ''
  formData.value[fieldKey] = url
  emit('update:modelValue', { ...formData.value })
}

function handleUploadRemove(_file: any, fieldKey: string) {
  formData.value[fieldKey] = ''
  emit('update:modelValue', { ...formData.value })
}

// ---------------------------------------------------------------------------
// Task 7.4: Sub-table summary integration
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// SubTableField config helpers (Req 10.1, 10.2, 10.3)
// ---------------------------------------------------------------------------
function getSubFormRowFormulas(bindingId?: number) {
  if (!bindingId || !props.config?.subForms) return undefined
  return props.config.subForms[String(bindingId)]?.rowFormulas
}

function getSummaryColumns(bindingId?: number) {
  if (!bindingId || !props.config?.summaryRules) return undefined
  return props.config.summaryRules
    .filter(r => r.sourceBindingId === bindingId)
    .map(r => r.sourceColumn)
}

function getSummaryAggregations(bindingId?: number) {
  if (!bindingId || !props.config?.summaryRules) return undefined
  const aggs: Record<string, 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'> = {}
  props.config.summaryRules
    .filter(r => r.sourceBindingId === bindingId)
    .forEach(r => { aggs[r.sourceColumn] = r.aggregation })
  return Object.keys(aggs).length > 0 ? aggs : undefined
}

function getSubTableValidation(bindingId?: number) {
  if (!bindingId || !props.config?.subTableValidation) return undefined
  return props.config.subTableValidation[String(bindingId)]
}

function handleSubTableUpdate(bindingId: number, rows: any[]) {
  emit('update:subTableData', bindingId, rows)

  // Trigger engine summary calculations
  if (props.config) {
    const summaryResult = engine.onSubTableChange(bindingId, rows, formData.value)
    for (const [targetField, value] of summaryResult.summaryValues) {
      formData.value[targetField] = value
      engineCalculatedValues.value.set(targetField, value)
    }
    engineCalculatedValues.value = new Map(engineCalculatedValues.value)
  }
}

// ---------------------------------------------------------------------------
// Watchers
// ---------------------------------------------------------------------------
watch(formData, (newVal) => {
  if (!isInternalUpdate && !props.readonly) {
    emit('update:modelValue', { ...newVal })
  }
}, { deep: true })

watch(() => props.modelValue, (newVal, oldVal) => {
  if (!isEqual(newVal, oldVal)) {
    initFormData()
  }
}, { deep: true })

watch(allFields, (newFields, oldFields) => {
  const hasChanged = newFields.length !== oldFields.length ||
    newFields.some((f, i) => f.key !== oldFields[i]?.key)
  if (hasChanged) {
    initFormData()
  }
})

watch(() => props.config, () => {
  initEngine()
}, { deep: true })

// ---------------------------------------------------------------------------
// Task 7.3: Form validation with engine integration
// ---------------------------------------------------------------------------

/**
 * Inject an engine validation error into an Element Plus form-item via DOM.
 * Adds the `is-error` class and appends an `.el-form-item__error` element.
 */
function injectFieldError(fieldKey: string, message: string) {
  const itemEl = document.querySelector(
    `.el-form-item[prop="${fieldKey}"]`
  ) as HTMLElement | null
  if (!itemEl) return
  itemEl.classList.add('is-error')
  const contentEl = itemEl.querySelector('.el-form-item__content')
  if (!contentEl) return
  // Remove any previously injected engine errors
  contentEl.querySelectorAll('.engine-error').forEach(el => el.remove())
  const errorDiv = document.createElement('div')
  errorDiv.className = 'el-form-item__error engine-error'
  errorDiv.textContent = message
  contentEl.appendChild(errorDiv)
}

/**
 * Clear all previously injected engine validation errors from the form.
 */
function clearEngineErrors() {
  document.querySelectorAll('.engine-error').forEach(el => el.remove())
  // Note: we don't remove is-error class here because Element Plus may have its own errors
}

const validate = async (): Promise<boolean> => {
  if (!formRef.value) return false

  // Clear previously injected engine errors before re-validating
  clearEngineErrors()

  let elPlusValid = true
  try {
    await formRef.value.validate()
  } catch {
    elPlusValid = false
  }

  // Engine validation (cross-field + custom rules)
  if (props.config) {
    const engineResult = engine.validateAll(formData.value)
    const crossResult = engine.validateCrossField(formData.value)

    if (!engineResult.valid || !crossResult.valid) {
      // Inject engine field errors into Element Plus form-item error state via DOM
      for (const [fieldKey, errors] of engineResult.fieldErrors) {
        if (errors.length > 0) {
          injectFieldError(fieldKey, errors[0])
        }
      }
      // Inject cross-field errors into targetField form-items
      for (const err of crossResult.errors) {
        injectFieldError(err.targetField, err.message)
      }
      // Scroll to first error field
      nextTick(() => {
        const firstError = document.querySelector('.el-form-item.is-error')
        firstError?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
      return false
    }
  }

  if (!elPlusValid) {
    // Scroll to first Element Plus error
    nextTick(() => {
      const firstError = document.querySelector('.el-form-item.is-error')
      if (firstError) {
        firstError.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    })
  }

  return elPlusValid
}

// ---------------------------------------------------------------------------
// Task 7.5: Auto-save to localStorage
// ---------------------------------------------------------------------------
const AUTO_SAVE_INTERVAL = 30_000 // 30 seconds
let autoSaveTimer: ReturnType<typeof setInterval> | null = null

function getAutoSaveKey(): string | null {
  if (props.functionUnitId && props.formId) {
    return `form_autosave_${props.functionUnitId}_${props.formId}`
  }
  return null
}

function autoSave() {
  const key = getAutoSaveKey()
  if (!key || props.readonly) return
  try {
    localStorage.setItem(key, JSON.stringify(formData.value))
  } catch (err) {
    console.warn('[FormRenderer] Auto-save to localStorage failed:', err)
  }
}

function startAutoSave() {
  stopAutoSave()
  if (getAutoSaveKey() && !props.readonly) {
    autoSaveTimer = setInterval(autoSave, AUTO_SAVE_INTERVAL)
  }
}

function stopAutoSave() {
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer)
    autoSaveTimer = null
  }
}

function clearAutoSave() {
  const key = getAutoSaveKey()
  if (key) {
    try {
      localStorage.removeItem(key)
    } catch (err) {
      console.warn('[FormRenderer] Failed to clear auto-save:', err)
    }
  }
  stopAutoSave()
}

async function checkAutoSaveRestore() {
  const key = getAutoSaveKey()
  if (!key || props.readonly) return

  try {
    const saved = localStorage.getItem(key)
    if (!saved) return

    const savedData = JSON.parse(saved)
    if (!savedData || typeof savedData !== 'object') return

    await ElMessageBox.confirm(
      t('formRenderer.autoSaveRestorePrompt'),
      t('formRenderer.autoSaveTitle'),
      {
        confirmButtonText: t('formRenderer.restore'),
        cancelButtonText: t('formRenderer.discard'),
        type: 'info',
      }
    )
    // User chose to restore
    isInternalUpdate = true
    formData.value = { ...formData.value, ...savedData }
    setTimeout(() => { isInternalUpdate = false }, 0)
    emit('update:modelValue', { ...formData.value })

    // Trigger engine re-evaluation for all restored fields (Req 12.1, 12.2)
    if (props.config) {
      for (const [key, value] of Object.entries(formData.value)) {
        if (value != null && value !== '') {
          const result = engine.onFieldChange(key, value, formData.value)
          applyEngineResult(result)
        }
      }
    }
  } catch {
    // User chose to discard or parse error — clear saved data
    clearAutoSave()
  }
}

// ---------------------------------------------------------------------------
// Existing exposed methods
// ---------------------------------------------------------------------------
const resetForm = () => {
  formRef.value?.resetFields()
  initFormData()
}

const getFormData = () => {
  return { ...formData.value }
}

const setFieldValue = (key: string, value: any) => {
  formData.value[key] = value
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------
onMounted(() => {
  initFormData()
  initEngine()
  // Task 7.5: Check for auto-saved data, then start auto-save timer
  checkAutoSaveRestore().then(() => {
    startAutoSave()
  })
})

onBeforeUnmount(() => {
  stopAutoSave()
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
.form-renderer {
  width: 100%;

  :deep(.el-form-item__label) {
    font-weight: 500;
    white-space: nowrap;
    padding-right: 16px;
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
