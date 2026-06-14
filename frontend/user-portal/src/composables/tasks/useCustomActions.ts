import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { applyAutoFill } from '@/utils/n8nAutoFillEngine'
import type { TaskActionInfo } from '@/api/task'
import type { FormField, FormTab, PortalViewContext } from '@/components/formRendererHelpers'
import { buildN8nAutoData } from './customActionN8n'
import { createCustomActionReturnFlows } from './customActionReturnFlows'
import { createCustomActionFormPopup } from './customActionFormPopup'
import type { PreparedFormPopupContext } from './customActionTypes'

export type { PreparedFormPopupContext } from './customActionTypes'

export function useCustomActions(options: {
  taskInfo: Ref<Record<string, any>>
  subTableBindings: Ref<any[]>
  formData: Ref<Record<string, any>>
  submitting: Ref<boolean>
  saveCurrentTaskForm: () => Promise<void>
  validateSubTableAssigneesForComplete: () => boolean
  approveDialogVisible: Ref<boolean>
  approveDialogTitle: Ref<string>
  currentApproveAction: Ref<string>
  approveForm: { comment: string }
  loadTaskDetail: () => Promise<void>
  /**
   * Resolve the popup's target form content (with tableBindings) from the host's
   * cached function-unit content. Returning null lets the composable surface a
   * "form not found" message without re-fetching.
   */
  resolveFormPopupContent?: (action: TaskActionInfo, config: any) => any | null
  /**
   * Build a FormRenderer-ready context (fields/tabs/subTableBindings/...) from
   * the popup form content + configJson. Implementations should reuse the host's
   * deriveColumnsFromBinding / resolveSubFormDesign / mergeLinkFormTargetBindingsInto
   * helpers so popup rendering matches the host's main-form rendering exactly.
   * Returning null leaves popup state empty (callers should surface an error).
   */
  preparePopupContext?: (formContent: any, formConfig: Record<string, unknown>) => PreparedFormPopupContext | null
}) {
  const { t } = useI18n()
  const router = useRouter()

  // N8N Action state
  const n8nActionDialogVisible = ref(false)
  const n8nActionDefinition = ref<{ id: number; actionName: string; configJson: string }>({ id: 0, actionName: '', configJson: '' })
  const n8nInitialData = ref<Record<string, any> | undefined>(undefined)

  // Form popup state
  const formPopupVisible = ref(false)
  const formPopupTitle = ref('')
  const formPopupFields = ref<FormField[]>([])
  const formPopupTabs = ref<FormTab[]>([])
  const formPopupData = ref<Record<string, any>>({})
  const formPopupReadOnly = ref(false)
  const formPopupWidth = ref('800px')
  const formPopupLabelWidth = ref('140px')
  const formPopupReadOnlyMode = ref(false)
  const currentFormPopupAction = ref<TaskActionInfo | null>(null)
  /**
   * Sub-table bindings prepared for the popup form by the host (via
   * preparePopupContext); FormRenderer needs these to resolve subTable widgets
   * declared in the popup's canvas rule.
   */
  const formPopupSubTableBindings = ref<any[]>([])
  const formPopupLinkedSubTableBindings = ref<any[] | null>(null)
  const formPopupNativeSubTableBindingIds = ref<number[]>([])
  const formPopupFormConfig = ref<Record<string, unknown>>({})
  const formPopupViewContext = ref<PortalViewContext>('assigneeTodo')

  // Return-style flows (ROLLBACK / DRAFT / WITHDRAW) — delegated, behavior unchanged.
  const { handleRollbackAction, handleDraftAction, handleWithdrawAction } = createCustomActionReturnFlows({
    t,
    router,
    taskInfo: options.taskInfo,
    submitting: options.submitting,
  })

  // FORM_POPUP open / submit / sub-table-update — delegated, behavior unchanged.
  const { openFormPopup, handleFormPopupSubTableUpdate, submitFormPopup } = createCustomActionFormPopup({
    t,
    taskInfo: options.taskInfo,
    submitting: options.submitting,
    loadTaskDetail: options.loadTaskDetail,
    resolveFormPopupContent: options.resolveFormPopupContent,
    preparePopupContext: options.preparePopupContext,
    formPopupVisible,
    formPopupTitle,
    formPopupFields,
    formPopupTabs,
    formPopupData,
    formPopupWidth,
    formPopupReadOnlyMode,
    currentFormPopupAction,
    formPopupSubTableBindings,
    formPopupLinkedSubTableBindings,
    formPopupNativeSubTableBindingIds,
    formPopupFormConfig,
    formPopupViewContext,
  })

  function handleCustomAction(action: TaskActionInfo) {
    const actionType = (action.actionType || '').trim().toUpperCase()
    switch (actionType) {
      case 'SAVE':
        options.saveCurrentTaskForm()
        break
      case 'APPROVE':
        if (!options.validateSubTableAssigneesForComplete()) return
        options.currentApproveAction.value = 'APPROVE'
        options.approveDialogTitle.value = action.actionName
        options.approveForm.comment = ''
        options.approveDialogVisible.value = true
        break
      case 'PROCESS_SUBMIT':
        if (!options.validateSubTableAssigneesForComplete()) return
        options.currentApproveAction.value = 'APPROVE'
        options.approveDialogTitle.value = action.actionName
        options.approveForm.comment = ''
        options.approveDialogVisible.value = true
        break
      case 'REJECT':
      case 'PROCESS_REJECT':
        options.currentApproveAction.value = 'REJECT'
        options.approveDialogTitle.value = action.actionName
        options.approveForm.comment = ''
        options.approveDialogVisible.value = true
        break
      case 'FORM_POPUP':
        try {
          const config = action.configJson ? JSON.parse(action.configJson) : {}
          openFormPopup(action, config)
        } catch {
          ElMessage.error(t('task.configParseFailed'))
        }
        break
      case 'ROLLBACK':
        handleRollbackAction(action)
        break
      case 'DRAFT':
        handleDraftAction(action)
        break
      case 'WITHDRAW':
        handleWithdrawAction(action)
        break
      case 'N8N_ACTION':
        try {
          const config = action.configJson ? JSON.parse(action.configJson) : {}
          const n8nAutoData = buildN8nAutoData(config, options.subTableBindings.value)
          n8nActionDefinition.value = { id: Number(action.actionId) || 0, actionName: action.actionName, configJson: action.configJson ?? '' }
          n8nInitialData.value = Object.keys(n8nAutoData).length > 0 ? n8nAutoData : undefined
          n8nActionDialogVisible.value = true
        } catch {
          ElMessage.error(t('task.configParseFailed'))
        }
        break
      default:
        ElMessage.warning(t('task.unknownActionType', { type: action.actionType }))
    }
  }

  function handleN8nActionExecuted(data: Record<string, any> | null) {
    if (!data) return
    try {
      const config = n8nActionDefinition.value.configJson ? JSON.parse(n8nActionDefinition.value.configJson) : {}
      const outputMapping = config.frontendOutputMapping
      if (outputMapping) {
        const result = applyAutoFill(
          data,
          outputMapping,
          options.subTableBindings.value,
          options.formData.value,
        )
        if (result.updatedBindings) {
          for (const b of options.subTableBindings.value) {
            const updated = result.updatedBindings.find((x: any) => x.bindingId === b.bindingId)
            if (updated) b.data = updated.data
          }
        }
        if (result.updatedFormData) {
          options.formData.value = { ...options.formData.value, ...result.updatedFormData }
        }
        const filledCount = result.filledCount || 0
        if (filledCount > 0) {
          ElMessage.success(t('processStart.n8nAutoFillSuccess', { count: filledCount }))
        }
      }
    } catch {
      // ignore auto-fill errors
    }
  }

  return {
    n8nActionDialogVisible,
    n8nActionDefinition,
    n8nInitialData,
    formPopupVisible,
    formPopupTitle,
    formPopupFields,
    formPopupTabs,
    formPopupData,
    formPopupReadOnly,
    formPopupWidth,
    formPopupLabelWidth,
    formPopupSubTableBindings,
    formPopupLinkedSubTableBindings,
    formPopupNativeSubTableBindingIds,
    formPopupFormConfig,
    formPopupViewContext,
    currentFormPopupAction,
    handleCustomAction,
    handleN8nActionExecuted,
    openFormPopup,
    submitFormPopup,
    handleFormPopupSubTableUpdate,
  }
}
