import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { submitTaskForm } from '@/api/processForm'
import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import {
  cloneSubTableRows,
  mergeSubTableRowsByRowId,
  getSavedSubTableRows,
  normalizeSubTableName,
  flattenNestedSubTableRowsIntoPayload,
  scrubMiCorruptLinkChildRowsForParent
} from './shared'

export function useTaskForm(options: {
  subTableBindings: Ref<any[]>
  isMiSubTaskMode: Ref<boolean>
  isCompletedTask: Ref<boolean>
  effectiveTaskId: Ref<string>
  taskFormDTO?: Ref<{ fieldValues?: Record<string, any> } | null>
  onFormReadOnlyChange?: (readonly: boolean) => void
}) {
  const { t } = useI18n()

  // Form state
  const formFields = ref<FormField[]>([])
  const formTabs = ref<FormTab[]>([])
  const formData = ref<Record<string, any>>({})
  const currentFormName = ref('')
  const formReadOnly = ref(false)
  const formLabelWidth = ref('160px')
  const savingTaskForm = ref(false)
  const taskFormDTO = options.taskFormDTO ?? ref<{ fieldValues?: Record<string, any> } | null>(null)
  let subTableAutosaveTimer: ReturnType<typeof setTimeout> | null = null

  function buildSubTableSubmitPayload() {
    const subTables: Record<string, any> = { ...((formData.value.__subTables__ as Record<string, any>) || {}) }
    flattenNestedSubTableRowsIntoPayload(subTables as Record<string, unknown>)
    if (options.isMiSubTaskMode.value) {
      const ci = (formData.value._currentItem ?? formData.value.currentItem) as
        | { rowId?: string | number; rowKey?: { id?: string | number } }
        | undefined
      const parentIdIdw = ci?.rowId ?? ci?.rowKey?.id
      if (parentIdIdw != null && String(parentIdIdw).trim() !== '') {
        scrubMiCorruptLinkChildRowsForParent(subTables as Record<string, unknown>, parentIdIdw)
      }
    }
    const subTableData: Record<string, Array<Record<string, unknown>>> = {}

    for (const binding of options.subTableBindings.value) {
      const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
      const existing = getSavedSubTableRows(subTables, binding)
      const merged = options.isMiSubTaskMode.value
        ? mergeSubTableRowsByRowId(
            existing,
            rows,
            Array.isArray((binding as { primaryKeyFields?: string[] }).primaryKeyFields)
              ? (binding as { primaryKeyFields?: string[] }).primaryKeyFields
              : null
          )
        : rows
      const out = cloneSubTableRows(merged)
      subTables[binding.bindingId] = out
      subTables[String(binding.bindingId)] = out
      subTableData[String(binding.bindingId)] = out
      if (binding.tableName) {
        subTables[binding.tableName] = out
        subTables[normalizeSubTableName(binding.tableName)] = out
        subTableData[binding.tableName] = out
      }
    }

    return {
      formData: { __subTables__: subTables },
      subTableData
    }
  }

  function buildCurrentTaskFormSubmitPayload() {
    const subTablePayload = buildSubTableSubmitPayload()
    return {
      formData: {
        ...formData.value,
        ...subTablePayload.formData
      },
      subTableData: subTablePayload.subTableData,
      baselineValues: taskFormDTO.value?.fieldValues || {}
    }
  }

  async function saveCurrentTaskForm() {
    if (formReadOnly.value || !options.effectiveTaskId.value) return
    savingTaskForm.value = true
    try {
      await submitTaskForm(options.effectiveTaskId.value, buildCurrentTaskFormSubmitPayload())
      ElMessage.success(t('task.operationSuccess'))
    } catch (error) {
      console.error('[TaskForm] save failed:', error)
      ElMessage.error(t('task.operationFailed'))
    } finally {
      savingTaskForm.value = false
    }
  }

  function scheduleSubTableAutosave() {
    if (formReadOnly.value || options.isCompletedTask.value || options.isMiSubTaskMode.value) return
    if (!options.effectiveTaskId.value) return
    if (subTableAutosaveTimer) clearTimeout(subTableAutosaveTimer)

    subTableAutosaveTimer = setTimeout(async () => {
      subTableAutosaveTimer = null
      try {
        await submitTaskForm(options.effectiveTaskId.value, {
          ...buildSubTableSubmitPayload(),
          baselineValues: {}
        })
      } catch (error) {
        console.error('[SubTable] autosave failed:', error)
      }
    }, 400)
  }

  function getCurrentFormFieldKeys(): string[] {
    const keys = new Set<string>()
    formFields.value.forEach((f: any) => {
      if (f?.key) keys.add(String(f.key))
    })
    formTabs.value.forEach((tab: any) => {
      ;(tab?.fields || []).forEach((f: any) => {
        if (f?.key) keys.add(String(f.key))
      })
    })
    return Array.from(keys)
  }

  function clearAutosaveTimer() {
    if (subTableAutosaveTimer) {
      clearTimeout(subTableAutosaveTimer)
      subTableAutosaveTimer = null
    }
  }

  return {
    formFields,
    formTabs,
    formData,
    currentFormName,
    formReadOnly,
    formLabelWidth,
    savingTaskForm,
    taskFormDTO,
    saveCurrentTaskForm,
    buildCurrentTaskFormSubmitPayload,
    buildSubTableSubmitPayload,
    scheduleSubTableAutosave,
    getCurrentFormFieldKeys,
    clearAutosaveTimer
  }
}
