import { computed, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FieldDefinition, FormDefinition } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import type { SubTableFieldDTO } from '@/api/subTableView'
import { collectSubTableRules } from '@/utils/formDesigner'
import {
  prepareFormCreateRulesForPersist,
  serializeFormCreateOptionsForPersist,
  walkRulesEnsureComponentEvents,
} from '@/utils/formCreateDefaultEvents'
import { ensureFormCreateRulesValidationDeep } from '@/utils/formCreateValidateRules'
import {
  commitDesignerPanelEditsBeforePreview,
  flushDesignerValidatePanelToActiveRule,
} from '@/utils/formDesignerPreviewValidation'
import { walkRulesApplyTableFieldDefaultsToPersistedRules } from '@/utils/formCreateRuleDefaults'
import { stripFormCreateRulesDisabledDeep } from '@/utils/formCreateRuleUtils'
import type { SubTableListColumnDTO } from './useSubTableViews'
import type { PortalViewsValue } from './useSubTablePortalViews'

type DesignerLike = { getRule?: () => unknown[]; setRule?: (r: unknown[]) => void } | null | undefined

interface UseFormSaveOptions {
  functionUnitId: number
  store: { updateForm: (functionUnitId: number, formId: number, payload: Record<string, any>) => Promise<any> }
  selectedForm: Ref<FormDefinition | null>
  designerRef: Ref<any>
  subDesignerRefs: Ref<any[]>
  designerSubBindings: ComputedRef<Array<{ bindingId: number; bindingType: string }>>
  subFormCache: Ref<Record<number, { rule: any[]; options: any }>>
  relationViewState: Ref<Record<number, { allFields: any[]; viewFields: any[] }>>
  subTableViewState: Ref<Record<number, { allFields: SubTableFieldDTO[]; viewFields: SubTableListColumnDTO[] }>>
  subTableListViewRefs: Ref<Record<number, any>>
  subTablePortalViewsState: Ref<Record<number, PortalViewsValue>>
  getActiveDesignerRef: () => DesignerLike
  getPrimaryBindingFieldDefinitions: () => FieldDefinition[]
  syncSubTableListViewFromFormRules: (bindingId: number, rule: any[]) => void
  loadForms: () => Promise<void>
  autoSaving: Ref<boolean>
  lastAutoSaveTime: Ref<Date | null>
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * Form persistence for FormDesigner: collects main/sub designer rules,
 * relation/list view state and portalViews into configJson and saves; also
 * owns Data_Table field-name validation and TASK field permission editing.
 */
export function useFormSave(options: UseFormSaveOptions) {
  const {
    functionUnitId, store, selectedForm, designerRef, subDesignerRefs, designerSubBindings,
    subFormCache, relationViewState, subTableViewState, subTableListViewRefs,
    subTablePortalViewsState, getActiveDesignerRef, getPrimaryBindingFieldDefinitions,
    syncSubTableListViewFromFormRules, loadForms, autoSaving, lastAutoSaveTime, t,
  } = options

  // Data_Table columns for field name autocomplete/validation
  const dataTableColumns = ref<string[]>([])

  /** Load Data_Table columns for field name autocomplete */
  async function loadDataTableColumns() {
    try {
      const res = await functionUnitApi.getDataTableColumns(functionUnitId)
      dataTableColumns.value = res?.data || []
    } catch {
      dataTableColumns.value = []
    }
  }

  /** Validate field names against Data_Table columns */
  function validateFieldNames(fieldNames: string[]): string[] {
    if (dataTableColumns.value.length === 0) return []
    return fieldNames.filter(name => !dataTableColumns.value.includes(name))
  }

  /** Get current form fields from the designer for field permission config */
  const currentFormFields = computed(() => {
    if (!designerRef.value || !selectedForm.value) return []
    try {
      const rule = designerRef.value.getRule() || []
      return rule
        .filter((r: any) => r.field && r.type !== 'subTable')
        .map((r: any) => ({ field: r.field, title: r.title || r.field }))
    } catch {
      // Fallback to saved configJson
      const rule = selectedForm.value.configJson?.rule || []
      return rule
        .filter((r: any) => r.field && r.type !== 'subTable')
        .map((r: any) => ({ field: r.field, title: r.title || r.field }))
    }
  })

  /** Get field permission value */
  function getFieldPermission(fieldName: string): string {
    return selectedForm.value?.fieldPermissions?.[fieldName] || 'EDITABLE'
  }

  /** Set field permission value */
  function setFieldPermission(fieldName: string, value: string) {
    if (!selectedForm.value) return
    if (!selectedForm.value.fieldPermissions) {
      selectedForm.value.fieldPermissions = {}
    }
    selectedForm.value.fieldPermissions[fieldName] = value
  }

  async function handleSaveForm(isManual = false) {
    if (!selectedForm.value || !designerRef.value) return

    if (!isManual) {
      autoSaving.value = true
    }

    try {
      commitDesignerPanelEditsBeforePreview()
      flushDesignerValidatePanelToActiveRule(getActiveDesignerRef())
      Object.values(subDesignerRefs.value).forEach((subRef) => {
        if (subRef) flushDesignerValidatePanelToActiveRule(subRef as Parameters<typeof flushDesignerValidatePanelToActiveRule>[0])
      })

      const rule = stripFormCreateRulesDisabledDeep(designerRef.value.getRule() || []) as any[]
      ensureFormCreateRulesValidationDeep(rule)
      walkRulesApplyTableFieldDefaultsToPersistedRules(rule, getPrimaryBindingFieldDefinitions())
      prepareFormCreateRulesForPersist(rule)
      walkRulesEnsureComponentEvents(rule)
      const options = serializeFormCreateOptionsForPersist(
        designerRef.value.getOption() as Record<string, unknown>,
      )

      const subTableRules = collectSubTableRules(rule)

      // Validate: all subTable placeholders must have a _bindingId selected
      const invalidPlaceholders = subTableRules.filter((r: any) => !r._bindingId)
      if (invalidPlaceholders.length > 0) {
        if (isManual) ElMessage.error(t('form.subTableBindingRequired'))
        return
      }

      // 子表占位符必须绑定 SUB 类型表绑定（流程/任务表单下一主多子，数据走子表单增删改）
      if (selectedForm.value.formType === 'PROCESS' || selectedForm.value.formType === 'TASK') {
        const boundSubTableRules = subTableRules.filter((r: any) => r._bindingId)
        // Use designerSubBindings for validation (includes latest bindings from store)
        const bindingMap = new Map(designerSubBindings.value.map(b => [b.bindingId, b.bindingType]))
        for (const st of boundSubTableRules) {
          const bindingType = bindingMap.get(st._bindingId)
          if (!bindingType || bindingType !== 'SUB') {
            if (isManual) ElMessage.error(t('form.subTableOnlySubBinding'))
            return
          }
        }
      }

      // Validate field names against Data_Table columns (for PROCESS and TASK forms)
      if (selectedForm.value.formType === 'PROCESS' || selectedForm.value.formType === 'TASK') {
        const fieldNames = rule
          .filter((r: any) => r.field && r.type !== 'subTable')
          .map((r: any) => r.field as string)
        const invalidFields = validateFieldNames(fieldNames)
        if (invalidFields.length > 0) {
          if (isManual) ElMessage.error(t('form.fieldNameValidationFailed'))
          return
        }
      }

      // Collect sub form rules — prefer live ref, then cache, then previously saved
      const subForms: Record<number, { rule: any[]; options: any }> = {}
      designerSubBindings.value.forEach((binding, index) => {
        const subRef = subDesignerRefs.value[index]
        if (subRef) {
          // Tab is currently active and mounted
          try {
            flushDesignerValidatePanelToActiveRule(subRef as Parameters<typeof flushDesignerValidatePanelToActiveRule>[0])
            const liveRule = stripFormCreateRulesDisabledDeep(subRef.getRule() || []) as any[]
            ensureFormCreateRulesValidationDeep(liveRule)
            prepareFormCreateRulesForPersist(liveRule)
            const liveOptions = serializeFormCreateOptionsForPersist(
              subRef.getOption() as Record<string, unknown>,
            )
            subForms[binding.bindingId] = { rule: liveRule, options: liveOptions }
            // Also update cache
            subFormCache.value[binding.bindingId] = { rule: liveRule, options: liveOptions }
          } catch {}
        } else if (subFormCache.value[binding.bindingId]) {
          // Tab was visited but is now unmounted — use cache
          const cached = subFormCache.value[binding.bindingId]
          const cachedRule = stripFormCreateRulesDisabledDeep(cached.rule || []) as any[]
          prepareFormCreateRulesForPersist(cachedRule)
          subForms[binding.bindingId] = {
            rule: cachedRule,
            options: serializeFormCreateOptionsForPersist(cached.options),
          }
        } else {
          // Tab never visited — preserve previously saved data
          const existing = (selectedForm.value!.configJson?.subForms || {})[binding.bindingId]
          if (existing) {
            const existingRule = stripFormCreateRulesDisabledDeep(existing.rule || []) as any[]
            prepareFormCreateRulesForPersist(existingRule)
            subForms[binding.bindingId] = {
              rule: existingRule,
              options: serializeFormCreateOptionsForPersist(existing.options),
            }
          }
        }
      })

      // Incrementally add sub-form fields to list view columns (preserve link/lookup columns).
      designerSubBindings.value.forEach((binding) => {
        if (binding.bindingType !== 'SUB') return
        const subForm = subForms[binding.bindingId]
        if (subForm?.rule?.length) {
          syncSubTableListViewFromFormRules(binding.bindingId, subForm.rule)
        }
      })

      // Collect relation table view fields
      const relationViews: Record<number, { viewFields: any[]; allFields: any[] }> = {}
      designerSubBindings.value.forEach((binding) => {
        if (binding.bindingType === 'RELATED') {
          const state = relationViewState.value[binding.bindingId]
          if (state && (state.viewFields.length > 0 || state.allFields.length > 0)) {
            relationViews[binding.bindingId] = state
          } else {
            // Preserve previously saved data
            const existing = (selectedForm.value!.configJson?.relationViews || {})[binding.bindingId]
            if (existing) relationViews[binding.bindingId] = existing
          }
        }
      })

      // Collect sub-table list view columns, including dropped Link Form columns.
      const subListViews: Record<number, { columns: SubTableListColumnDTO[] }> = {
        ...(selectedForm.value.configJson?.subListViews || {})
      }
      designerSubBindings.value.forEach((binding) => {
        if (binding.bindingType !== 'SUB') return
        const listRef = subTableListViewRefs.value[binding.bindingId]
        if (listRef) {
          const columns = listRef.getListColumns?.() || listRef.getViewFields?.() || []
          const state = subTableViewState.value[binding.bindingId]
          const existing = (selectedForm.value!.configJson?.subListViews || {})[binding.bindingId]
          const existingColumns = Array.isArray(existing?.columns) ? existing.columns : []
          // Only treat list state as "ready" when we have columns in memory. allFields alone is not enough:
          // after a bad merge, viewFields can be empty while allFields is populated — saving would otherwise
          // persist { columns: [] } and wipe configJson.subListViews.
          const stateLoaded = !!state && (state.viewFields?.length || 0) > 0
          if (columns.length === 0 && existingColumns.length > 0 && !stateLoaded) {
            // The list-view tab can mount before its async config load finishes; preserve saved columns.
            subListViews[binding.bindingId] = existing
          } else {
            subListViews[binding.bindingId] = { columns }
            const nextState = state || { allFields: [], viewFields: [] }
            subTableViewState.value[binding.bindingId] = {
              ...nextState,
              viewFields: columns
            }
          }
        } else {
          const state = subTableViewState.value[binding.bindingId]
          if (state?.viewFields?.length) {
            subListViews[binding.bindingId] = { columns: state.viewFields }
          } else {
            const existing = (selectedForm.value!.configJson?.subListViews || {})[binding.bindingId]
            if (existing) subListViews[binding.bindingId] = existing
          }
        }
      })

      // Collect per-binding portalViews — start from previously saved config so untouched
      // bindings keep their settings, then overlay anything the designer edited in this session.
      const subTablePortalViews: Record<number, PortalViewsValue> = {
        ...(selectedForm.value.configJson?.subTablePortalViews || {}),
        ...subTablePortalViewsState.value
      }

      const nextConfig = { rule, options, subForms, relationViews, subListViews, subTablePortalViews }
      const updated = await store.updateForm(functionUnitId, selectedForm.value.id, {
        formName: selectedForm.value.formName,
        formType: selectedForm.value.formType,
        description: selectedForm.value.description,
        configJson: nextConfig,
        ...(selectedForm.value.formType === 'TASK' && selectedForm.value.fieldPermissions
          ? { fieldPermissions: selectedForm.value.fieldPermissions }
          : {})
      })
      selectedForm.value = {
        ...selectedForm.value,
        configJson: updated.configJson || nextConfig
      }

      if (isManual) {
        ElMessage.success(t('form.saveSuccess'))
        await loadForms()
      } else {
        lastAutoSaveTime.value = new Date()
      }
    } catch (e: any) {
      if (isManual) {
        ElMessage.error(e.response?.data?.message || t('form.saveFailed'))
      }
    } finally {
      if (!isManual) {
        autoSaving.value = false
      }
    }
  }

  return {
    dataTableColumns,
    loadDataTableColumns,
    validateFieldNames,
    currentFormFields,
    getFieldPermission,
    setFieldPermission,
    handleSaveForm,
  }
}
