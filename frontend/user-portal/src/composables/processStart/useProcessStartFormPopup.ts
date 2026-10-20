import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TaskActionInfo } from '@/api/task'
import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import type { PortalViewContext } from '@/components/formRendererHelpers'
import { createCustomActionFormPopup } from '@/composables/tasks/customActionFormPopup'
import { createProcessStartPopupHelpers } from './createProcessStartPopupHelpers'
import { submitProcessStartFormPopup } from '@/api/processForm'
import type { BatchStartContext } from '@/api/process'
import { warnIfUploadsBlocking } from '@platform-shared/upload/uploadSubmitGate'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

type StartActionButton = {
  id: string
  label: string
  actionType?: string
  configJson?: string
}

export function useProcessStartFormPopup(deps: {
  t: (key: string, named?: Record<string, unknown>) => string
  submitting: Ref<boolean>
  cachedContentForms: Ref<unknown[]>
  extractFieldsRecursive: (rules: unknown[]) => FormField[]
  deriveColumnsFromBinding: (b: unknown, subForms: Record<string, unknown>, cfg: Record<string, unknown>) => unknown[]
  functionUnitCode: Ref<string>
  batchStartContext: Ref<BatchStartContext | undefined>
  assembleStartPayload: () => {
    formData: Record<string, unknown>
    emptiedSubTableKeys: string[]
    subTableBindingScopes: unknown[]
  }
  validateMainForm: () => Promise<boolean>
  ensureMainPrimaryKey: () => Promise<void>
}) {
  const popupStartedInstanceId = ref<string | null>(null)
  const taskInfoStub = ref<Record<string, unknown>>({})

  const formPopupVisible = ref(false)
  const formPopupTitle = ref('')
  const formPopupFields = ref<FormField[]>([])
  const formPopupTabs = ref<FormTab[]>([])
  const formPopupData = ref<Record<string, unknown>>({})
  const formPopupWidth = ref('800px')
  const formPopupReadOnlyMode = ref(false)
  const currentFormPopupAction = ref<TaskActionInfo | null>(null)
  const formPopupSubTableBindings = ref<unknown[]>([])
  const formPopupLinkedSubTableBindings = ref<unknown[] | null>(null)
  const formPopupNativeSubTableBindingIds = ref<number[]>([])
  const formPopupFormConfig = ref<Record<string, unknown>>({})
  const formPopupViewContext = ref<PortalViewContext>('assigneeTodo')

  const popupHelpers = createProcessStartPopupHelpers({
    cachedContentForms: deps.cachedContentForms,
    extractFieldsRecursive: deps.extractFieldsRecursive,
    deriveColumnsFromBinding: deps.deriveColumnsFromBinding,
  })

  const { openFormPopup, handleFormPopupSubTableUpdate } = createCustomActionFormPopup({
    t: deps.t,
    taskInfo: taskInfoStub,
    submitting: deps.submitting,
    loadTaskDetail: async () => {},
    resolveFormPopupContent: popupHelpers.resolveFormPopupContent,
    preparePopupContext: popupHelpers.preparePopupContext,
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

  async function openFormPopupFromStart(action: StartActionButton) {
    let config: Record<string, unknown> = {}
    try {
      config = action.configJson ? JSON.parse(action.configJson) : {}
    } catch {
      ElMessage.error(deps.t('task.configParseFailed'))
      return
    }
    const taskAction: TaskActionInfo = {
      actionId: action.id,
      actionName: action.label,
      actionType: 'FORM_POPUP',
      configJson: action.configJson,
    }
    await openFormPopup(taskAction, config)
  }

  async function submitFormPopupFromStart() {
    const action = currentFormPopupAction.value
    if (!action?.actionId) {
      ElMessage.error(deps.t('task.formSubmitFailed'))
      return
    }
    if (!warnIfUploadsBlocking({
      inflight: deps.t('upload.waitUntilComplete'),
      failed: deps.t('upload.fixFailedBeforeSubmit'),
    })) return
    try {
      await deps.ensureMainPrimaryKey()
    } catch {
      ElMessage.error(deps.t('processStart.pkGenerateFailed'))
      return
    }
    if (!(await deps.validateMainForm())) {
      ElMessage.warning(deps.t('processStart.pleaseCompleteForm'))
      return
    }
    const procKey = deps.functionUnitCode.value
    if (!procKey) {
      ElMessage.error(deps.t('task.formSubmitFailed'))
      return
    }
    deps.submitting.value = true
    try {
      const assembled = deps.assembleStartPayload()
      const res = await submitProcessStartFormPopup(procKey, action.actionId, {
        processInstanceId: popupStartedInstanceId.value ?? undefined,
        popupFormData: formPopupData.value,
        formData: assembled.formData,
        batchStartContext: deps.batchStartContext.value,
        emptiedSubTableKeys: assembled.emptiedSubTableKeys,
        subTableBindingScopes: assembled.subTableBindingScopes,
      })
      const body = res as { data?: { id?: string }; id?: string }
      const id = body.data?.id ?? body.id
      if (id) popupStartedInstanceId.value = id
      ElMessage.success(deps.t('processStart.formPopupSubmitSuccess'))
      formPopupVisible.value = false
    } catch (error: unknown) {
      ElMessage.error(resolveUserFacingHttpMessage(error, deps.t) || deps.t('task.formSubmitFailed'))
    } finally {
      deps.submitting.value = false
    }
  }

  return {
    formPopupVisible,
    formPopupTitle,
    formPopupFields,
    formPopupTabs,
    formPopupData,
    formPopupWidth,
    formPopupReadOnlyMode: formPopupReadOnlyMode,
    formPopupSubTableBindings,
    formPopupLinkedSubTableBindings,
    formPopupNativeSubTableBindingIds,
    formPopupFormConfig,
    formPopupViewContext,
    openFormPopupFromStart,
    submitFormPopup: submitFormPopupFromStart,
    handleFormPopupSubTableUpdate,
  }
}
