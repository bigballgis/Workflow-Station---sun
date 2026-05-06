import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { processApi } from '@/api/process'
import { applyAutoFill } from '@/utils/n8nAutoFillEngine'
import type { TaskActionInfo } from '@/api/task'

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
}) {
  const { t } = useI18n()

  // N8N Action state
  const n8nActionDialogVisible = ref(false)
  const n8nActionDefinition = ref<{ id: number; actionName: string; configJson: string }>({ id: 0, actionName: '', configJson: '' })
  const n8nInitialData = ref<Record<string, any> | undefined>(undefined)

  // Form popup state
  const formPopupVisible = ref(false)
  const formPopupTitle = ref('')
  const formPopupFields = ref<any[]>([])
  const formPopupTabs = ref<any[]>([])
  const formPopupData = ref<Record<string, any>>({})
  const formPopupReadOnly = ref(false)
  const formPopupWidth = ref('800px')
  const formPopupLabelWidth = ref('140px')
  const formPopupReadOnlyMode = ref(false)
  const currentFormPopupAction = ref<TaskActionInfo | null>(null)

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

  async function openFormPopup(action: TaskActionInfo, config: any) {
    try {
      currentFormPopupAction.value = action
      formPopupTitle.value = config.popupTitle || action.actionName
      formPopupWidth.value = config.popupWidth || '800px'
      formPopupReadOnlyMode.value = config.readOnly === true || config.readOnly === 'true'
      formPopupData.value = {}
      if (config.formId) {
        const functionUnitId = options.taskInfo.value.processDefinitionKey
        if (functionUnitId) {
          try {
            const res = await processApi.getFunctionUnitContents(functionUnitId, 'FORM')
            const forms = (res as any).data || []
            const formContent = forms.find((f: any) => f.sourceId === String(config.formId) || f.contentName === config.formName)
            if (formContent?.contentData) {
              const formConfig = typeof formContent.contentData === 'string' ? JSON.parse(formContent.contentData) : formContent.contentData
              parseFormPopupConfig(formConfig)
              formPopupVisible.value = true
            } else {
              ElMessage.error(t('task.formNotFound', { name: config.formName || config.formId }))
            }
          } catch {
            ElMessage.error(t('task.formLoadFailed'))
          }
        }
      } else {
        ElMessage.error(t('task.formMissingId'))
      }
    } catch {
      ElMessage.error(t('task.formOpenFailed'))
    }
  }

  function parseFormPopupConfig(configInput: any) {
    try {
      const config = typeof configInput === 'string' ? JSON.parse(configInput) : configInput
      const rules = config.rule && Array.isArray(config.rule) ? config.rule : (Array.isArray(config) ? config : null)
      if (rules) {
        rules.forEach((r: any, i: number) => {
          if (r.type === 'el-tabs' && Array.isArray(r.children)) {
            formPopupTabs.value = r.children.map((tab: any) => ({
              name: tab.props?.label || tab.title || `Tab ${i + 1}`,
              fields: (tab.children || []).map((item: any) => convertFormCreateRuleSimple(item))
            }))
          }
        })
        if (formPopupTabs.value.length === 0) {
          formPopupFields.value = rules.map(convertFormCreateRuleSimple)
        }
      }
    } catch {
      // ignore parse errors
    }
  }

  function convertFormCreateRuleSimple(rule: any): any {
    return {
      key: rule.field || rule.id || '',
      label: rule.title || rule.name || '',
      type: rule.type || 'input',
      required: rule.props?.required || false,
      placeholder: rule.props?.placeholder || '',
      options: rule.options || [],
      defaultValue: rule.props?.defaultValue ?? rule.value ?? null,
      props: rule.props || {}
    }
  }

  async function submitFormPopup() {
    options.submitting.value = true
    try {
      // TODO: Implement form popup submission
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
    currentFormPopupAction,
    handleCustomAction,
    handleN8nActionExecuted,
    openFormPopup,
    submitFormPopup
  }
}
