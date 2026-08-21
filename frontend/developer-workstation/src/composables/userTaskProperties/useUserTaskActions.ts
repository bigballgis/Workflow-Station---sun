/**
 * UserTask 属性面板的动作（action）与表单数据加载逻辑。
 *
 * 涵盖动作选择处理、动作类型标签，以及表单 / 动作列表加载。行为零变化。
 */
import { computed } from 'vue'
import { functionUnitApi } from '@/api/functionUnit'
import type { UserTaskPropertyContext, UserTaskPropsAccessor } from './types'

export function useUserTaskActions(
  props: UserTaskPropsAccessor,
  ctx: UserTaskPropertyContext
) {
  const { actions, forms, requestFormId, requestFormName, updateExtProp, t } = ctx

  function handleActionsChange(ids: number[]) {
    updateExtProp('actionIds', ids)
    const actionNames = ids.map(id => {
      const action = actions.value.find(a => a.id === id)
      return action?.actionName || ''
    }).filter(Boolean)
    updateExtProp('actionNames', actionNames)
  }

  function handleFormChange(id: number | null) {
    updateExtProp('formId', id)
    const form = forms.value.find(f => f.id === id)
    if (form) {
      updateExtProp('formName', form.formName)
    }
  }

  /**
   * The My Requests design bound to this node — mirrors handleFormChange (To Do), but
   * writes requestFormId/requestFormName instead. Editable here in addition to Form
   * Design > My Requests > row menu > Bound Node; both paths write the same BPMN ext
   * props, so whichever was used last wins (last-write, same as any single BPMN field).
   */
  const requestableForms = computed(() =>
    forms.value.filter(f => (f.scene ?? 'TASK') === 'REQUEST' && f.formType !== 'DETAIL'))

  function handleRequestFormChange(id: number | null) {
    requestFormId.value = id
    updateExtProp('requestFormId', id)
    const form = id != null ? forms.value.find(f => f.id === id) : undefined
    requestFormName.value = form?.formName || ''
    updateExtProp('requestFormName', requestFormName.value)
  }

  const actionTypeLabel = (type: string) => {
    const map: Record<string, string> = {
      APPROVE: t('action.approve'),
      REJECT: t('action.reject'),
      TRANSFER: t('action.transfer'),
      DELEGATE: t('action.delegate'),
      ROLLBACK: t('action.rollback'),
      WITHDRAW: t('action.withdraw'),
      DRAFT: t('action.draft'),
      SAVE: t('action.saveDraft'),
      PROCESS_SUBMIT: t('action.processSubmit'),
      PROCESS_REJECT: t('action.processReject'),
      COMPOSITE: t('action.composite'),
      API_CALL: t('action.apiCall'),
      FORM_POPUP: t('action.formPopup'),
      CUSTOM_SCRIPT: t('action.customScript')
    }
    return map[type] || type
  }

  async function loadForms() {
    try {
      const res = await functionUnitApi.getForms(props.functionUnitId)
      forms.value = res.data || []
    } catch {
      forms.value = []
    }
  }

  async function loadActions() {
    try {
      const res = await functionUnitApi.getActions(props.functionUnitId)
      actions.value = res.data || []
    } catch {
      actions.value = []
    }
  }

  return {
    handleActionsChange,
    handleFormChange,
    requestableForms,
    handleRequestFormChange,
    actionTypeLabel,
    loadForms,
    loadActions
  }
}
