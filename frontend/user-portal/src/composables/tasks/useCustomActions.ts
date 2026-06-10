import { ref, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { completeTask, getReturnableActivities, type ReturnableActivity } from '@/api/task'
import { processApi } from '@/api/process'
import { applyAutoFill } from '@/utils/n8nAutoFillEngine'
import { resolveRollbackTargetActivityId } from '@/utils/taskReturnTarget'
import type { TaskActionInfo } from '@/api/task'
import type { FormField, FormTab, PortalViewContext } from '@/components/formRendererHelpers'

/**
 * Prepared FORM_POPUP rendering context — built by the host view from the
 * popup's target form content (configJson + tableBindings + cachedContentForms).
 * Mirrors what FormRenderer needs to render the popup at parity with the
 * Designer Form Preview (subTable widgets, Link Form targets, portalViews).
 */
export interface PreparedFormPopupContext {
  fields: FormField[]
  tabs: FormTab[]
  subTableBindings: any[]
  linkedSubTableBindings?: any[] | null
  nativeSubTableBindingIds: number[]
  formConfig: Record<string, unknown>
  viewContext?: PortalViewContext
}

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
          const n8nAutoData: Record<string, any> = {}
          if (config.inputMapping?.source === 'sub_table') {
            const bindingName = config.inputMapping.subTableName
            if (bindingName) {
              const binding = options.subTableBindings.value.find(
                (b: any) => b.tableName === bindingName || String(b.bindingId) === bindingName
              )
              if (binding) {
                const rows = Array.isArray(binding.data) ? binding.data : []
                n8nAutoData.data = rows
                const fileFields = (config.inputMapping.fileFields || []) as string[]
                const fileUrls: string[] = []
                rows.forEach((row: any) => {
                  fileFields.forEach((field: string) => {
                    const cell = row?.[field]
                    if (Array.isArray(cell)) {
                      cell.forEach((f: any) => { if (f?.url) fileUrls.push(f.url) })
                    } else if (cell?.url) {
                      fileUrls.push(cell.url)
                    }
                  })
                })
                if (fileUrls.length > 0) n8nAutoData.files = fileUrls
              }
            }
          }
          n8nActionDefinition.value = { id: Number(action.actionId) || 0, actionName: action.actionName, configJson: action.configJson }
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

  async function handleReturnToActivityAction(
    action: TaskActionInfo,
    targetStep: string,
    completeAction: 'DRAFT' | 'RETURN',
    messages: {
      noTask: string
      noTarget: string
      confirm: (node: string) => string
      confirmTitle: string
      success: string
      failed: string
    },
  ) {
    const taskId =
      (options.taskInfo.value?.id ?? options.taskInfo.value?.taskId) as string | undefined
    if (!taskId) {
      ElMessage.error(messages.noTask)
      return
    }
    let config: Record<string, unknown> = {}
    try {
      config = action.configJson ? JSON.parse(action.configJson) : {}
    } catch {
      config = {}
    }

    let comment = ''
    if (config.requireComment === true) {
      try {
        const { value } = await ElMessageBox.prompt(
          t('task.commentPlaceholder'),
          t('task.return'),
          {
            confirmButtonText: t('common.confirm'),
            cancelButtonText: t('common.cancel'),
            inputValidator: (v) =>
              v != null && String(v).trim() !== '' ? true : t('task.commentRequired'),
          },
        )
        comment = String(value).trim()
      } catch {
        return
      }
    }

    options.submitting.value = true
    try {
      const res = await getReturnableActivities(taskId)
      const activities = (res as { data?: ReturnableActivity[] })?.data ?? (res as ReturnableActivity[])
      const list = Array.isArray(activities) ? activities : []
      const target = resolveRollbackTargetActivityId(targetStep, config, list)
      if (!target) {
        ElMessage.error(messages.noTarget)
        return
      }
      const nodeLabel = target.taskName || target.activityId
      const confirmMsg =
        (typeof config.confirmMessage === 'string' && config.confirmMessage.trim())
          ? String(config.confirmMessage).trim()
          : messages.confirm(nodeLabel)
      try {
        await ElMessageBox.confirm(confirmMsg, messages.confirmTitle, { type: 'warning' })
      } catch {
        return
      }
      await completeTask(taskId, {
        taskId,
        action: completeAction,
        comment,
        returnActivityId: target.activityId,
      })
      ElMessage.success(messages.success)
      await router.push('/tasks')
    } catch (err: unknown) {
      const msg =
        err && typeof err === 'object' && 'message' in err
        && typeof (err as { message: unknown }).message === 'string'
          ? (err as { message: string }).message
          : messages.failed
      ElMessage.error(msg)
    } finally {
      options.submitting.value = false
    }
  }

  async function handleRollbackAction(action: TaskActionInfo) {
    let config: Record<string, unknown> = {}
    try {
      config = action.configJson ? JSON.parse(action.configJson) : {}
    } catch {
      config = {}
    }
    const targetStep =
      typeof config.targetStep === 'string' && config.targetStep.trim()
        ? config.targetStep
        : 'previous'
    await handleReturnToActivityAction(action, targetStep, 'RETURN', {
      noTask: t('task.rollbackNoTask'),
      noTarget: t('task.rollbackNoTarget'),
      confirm: (node) => t('task.rollbackConfirm', { node }),
      confirmTitle: t('task.rollbackConfirmTitle'),
      success: t('task.rollbackSuccess'),
      failed: t('task.rollbackFailed'),
    })
  }

  async function handleDraftAction(action: TaskActionInfo) {
    await handleReturnToActivityAction(action, 'first', 'DRAFT', {
      noTask: t('task.draftNoTask'),
      noTarget: t('task.draftNoTarget'),
      confirm: (node) => t('task.draftConfirm', { node }),
      confirmTitle: t('task.draftConfirmTitle'),
      success: t('task.draftSuccess'),
      failed: t('task.draftFailed'),
    })
  }

  async function handleWithdrawAction(action: TaskActionInfo) {
    const processId = options.taskInfo.value?.processInstanceId as string | undefined
    if (!processId) {
      ElMessage.error(t('task.withdrawNoProcess'))
      return
    }
    let config: Record<string, unknown> = {}
    try {
      config = action.configJson ? JSON.parse(action.configJson) : {}
    } catch {
      config = {}
    }
    const confirmMsg =
      (typeof config.confirmMessage === 'string' && config.confirmMessage.trim())
        ? config.confirmMessage
        : t('applicationDetail.withdrawConfirm')
    try {
      await ElMessageBox.confirm(confirmMsg, t('applicationDetail.withdrawConfirmTitle'), {
        type: 'warning',
      })
    } catch {
      return
    }
    let reason =
      (typeof config.defaultReason === 'string' && config.defaultReason.trim())
        ? config.defaultReason
        : t('applicationDetail.userWithdraw')
    if (config.requireComment === true || config.requireReason === true) {
      try {
        const { value } = await ElMessageBox.prompt(
          t('task.commentPlaceholder'),
          t('task.reason'),
          {
            confirmButtonText: t('common.confirm'),
            cancelButtonText: t('common.cancel'),
            inputValidator: (v) => (v != null && String(v).trim() !== '' ? true : t('task.commentRequired')),
          },
        )
        reason = String(value).trim()
      } catch {
        return
      }
    }
    options.submitting.value = true
    try {
      await processApi.withdrawProcess(processId, reason)
      const successMsg =
        (typeof config.successMessage === 'string' && config.successMessage.trim())
          ? config.successMessage
          : t('applicationDetail.withdrawSuccess')
      ElMessage.success(successMsg)
      await router.push('/tasks')
    } catch (err: unknown) {
      const msg =
        err && typeof err === 'object' && 'message' in err && typeof (err as { message: unknown }).message === 'string'
          ? (err as { message: string }).message
          : t('applicationDetail.withdrawFailed')
      ElMessage.error(msg)
    } finally {
      options.submitting.value = false
    }
  }

  function handleN8nActionExecuted(data: Record<string, any> | null) {
    if (!data) return
    try {
      const config = n8nActionDefinition.value.configJson ? JSON.parse(n8nActionDefinition.value.configJson) : {}
      const outputMapping = config.frontendOutputMapping
      if (outputMapping) {
        const result = applyAutoFill({
          output: data,
          outputMapping,
          subTableBindings: options.subTableBindings.value,
          formData: options.formData.value
        })
        if (result.subTableBindings) {
          for (const b of options.subTableBindings.value) {
            const updated = result.subTableBindings.find((x: any) => x.bindingId === b.bindingId)
            if (updated) b.data = updated.data
          }
        }
        if (result.formData) {
          options.formData.value = { ...options.formData.value, ...result.formData }
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

  /**
   * Open the FORM_POPUP dialog with parity to the Designer Form Preview:
   *  1) Resolve the target form content (with tableBindings) via the host-provided
   *     `resolveFormPopupContent` callback (typically reads from cachedContentForms);
   *     falls back to `processApi.getFunctionUnitContent` so legacy callers still work.
   *  2) Delegate parsing + sub-table binding assembly to the host via
   *     `preparePopupContext`, which uses the same helpers as the main form (so
   *     subTable / lookup / card / Link Form widgets render identically).
   * Designer must enforce subTable widgets only on PRIMARY-style ACTION forms;
   * popup rendering does not attempt to write back sub-table data without the
   * host's submission integration.
   */
  async function openFormPopup(action: TaskActionInfo, config: any) {
    try {
      currentFormPopupAction.value = action
      formPopupTitle.value = config.popupTitle || action.actionName
      formPopupWidth.value = config.popupWidth || '800px'
      formPopupReadOnlyMode.value = config.readOnly === true || config.readOnly === 'true'
      formPopupData.value = {}
      formPopupFields.value = []
      formPopupTabs.value = []
      formPopupSubTableBindings.value = []
      formPopupLinkedSubTableBindings.value = null
      formPopupNativeSubTableBindingIds.value = []
      formPopupFormConfig.value = {}
      formPopupViewContext.value = 'assigneeTodo'

      if (!config.formId) {
        ElMessage.error(t('task.formMissingId'))
        return
      }

      // Prefer host-supplied form content (already includes tableBindings + sourceId).
      let formContent: any = null
      if (options.resolveFormPopupContent) {
        formContent = options.resolveFormPopupContent(action, config)
      }

      // Fallback for hosts that don't supply a resolver — fetch full FU content directly
      // (legacy parity; still returns tableBindings on each form despite slim TS types).
      if (!formContent) {
        const functionUnitId = options.taskInfo.value.processDefinitionKey
        if (functionUnitId) {
          try {
            const res = await processApi.getFunctionUnitContent(functionUnitId)
            const content = ('data' in (res as any) ? (res as any).data : res) as any
            const forms = content?.forms || []
            formContent =
              forms.find((f: any) => String(f.sourceId) === String(config.formId)) ||
              (config.formName ? forms.find((f: any) => f.name === config.formName) : null) ||
              null
          } catch {
            ElMessage.error(t('task.formLoadFailed'))
            return
          }
        }
      }

      if (!formContent) {
        ElMessage.error(t('task.formNotFound', { name: config.formName || config.formId }))
        return
      }

      const rawData = formContent.data ?? formContent.contentData
      let formConfig: Record<string, unknown> = {}
      try {
        formConfig = typeof rawData === 'string' ? JSON.parse(rawData) : (rawData || {})
      } catch {
        ElMessage.error(t('task.formLoadFailed'))
        return
      }

      if (!options.preparePopupContext) {
        // Without a host-side preparer the composable cannot build sub-table
        // bindings safely (helpers live in tasks/detail.vue). Refuse to fall back
        // to the legacy simplified converter — that path silently dropped
        // subTable / card / lookup widgets and is the root cause of #1394.
        console.warn('[useCustomActions] preparePopupContext not provided — popup will not render')
        ElMessage.error(t('task.formOpenFailed'))
        return
      }

      const ctx = options.preparePopupContext(formContent, formConfig)
      if (!ctx) {
        ElMessage.error(t('task.formOpenFailed'))
        return
      }

      formPopupFields.value = ctx.fields
      formPopupTabs.value = ctx.tabs
      formPopupSubTableBindings.value = ctx.subTableBindings
      formPopupLinkedSubTableBindings.value = ctx.linkedSubTableBindings ?? null
      formPopupNativeSubTableBindingIds.value = ctx.nativeSubTableBindingIds
      formPopupFormConfig.value = ctx.formConfig
      formPopupViewContext.value = ctx.viewContext ?? 'assigneeTodo'
      formPopupVisible.value = true
    } catch (e) {
      console.warn('[useCustomActions] openFormPopup failed:', e)
      ElMessage.error(t('task.formOpenFailed'))
    }
  }

  function handleFormPopupSubTableUpdate(bindingId: number, rows: any[]) {
    const target = formPopupSubTableBindings.value.find(
      (b: any) => Number(b?.bindingId) === Number(bindingId),
    )
    if (target) {
      target.data = rows
    }
  }

  async function submitFormPopup() {
    options.submitting.value = true
    try {
      // Stub: no server endpoint for ACTION form popup submit yet; dialog closes after ack.
      // Sub-table edits stay in formPopupSubTableBindings for future submit wiring.
      ElMessage.success(t('task.formSubmitSuccess'))
      formPopupVisible.value = false
      options.loadTaskDetail()
    } catch {
      ElMessage.error(t('task.formSubmitFailed'))
    } finally {
      options.submitting.value = false
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
