import { reactive, computed, watch } from 'vue'
import type { Ref } from 'vue'
import type { ActionDefinition } from '@/api/functionUnit'
import type { ConditionExpression } from '@/components/designer/formBusinessLogicTypes'

interface UseActionConfigOptions {
  selectedAction: Ref<ActionDefinition | null>
  store: {
    forms: Array<{
      id: number
      formName: string
      formType: string
      configJson?: { rule?: Array<{ field?: string; type?: string }> }
    }>
  }
  // 注入回调（wrapper 闭包破环，避免与 binding composable 的循环依赖）
  loadActionBinding: (actionId: string | number) => void
}

/**
 * 动作配置状态：actionConfig reactive、随 selectedAction 同步的 watch，
 * 以及表单/角色相关的 computed。
 */
export function useActionConfig(options: UseActionConfigOptions) {
  const { selectedAction, store, loadActionBinding } = options

  // FORM_POPUP action: only show ACTION type forms
  const actionFormOptions = computed(() => store.forms.filter(f => f.formType === 'ACTION'))
  const availableFormFields = computed(() => {
    const fields = new Set<string>()
    for (const form of store.forms) {
      const rule = form.configJson?.rule
      if (Array.isArray(rule)) {
        for (const r of rule) {
          if (r.field && r.type !== 'subTable') {
            fields.add(r.field)
          }
        }
      }
    }
    return Array.from(fields)
  })

  const actionConfig = reactive<Record<string, any>>({
    url: '',
    method: 'POST',
    headers: '',
    body: '',
    formId: null,
    dialogTitle: '',
    dialogWidth: '600px',
    requireComment: false,
    confirmMessage: '',
    script: '',
    targetStatus: '',
    requireAssignee: false,
    targetStep: '',
    // Visibility, roles & sort order
    visibilityCondition: null as ConditionExpression[] | null,
    allowedRoles: [] as string[],
    sortOrder: 0
  })

  watch(selectedAction, (action) => {
    if (action?.configJson) {
      Object.assign(actionConfig, action.configJson)
    } else {
      // Reset to defaults
      Object.assign(actionConfig, {
        url: '',
        method: 'POST',
        headers: '',
        body: '',
        formId: null,
        dialogTitle: '',
        dialogWidth: '600px',
        requireComment: false,
        confirmMessage: '',
        script: '',
        targetStatus: '',
        requireAssignee: false,
        targetStep: '',
        // Visibility, roles & sort order
        visibilityCondition: null as ConditionExpression[] | null,
        allowedRoles: [] as string[],
        sortOrder: 0
      })
    }

    // 加载当前动作的绑定信息
    if (action) {
      loadActionBinding(action.id)
    }
  })

  return {
    actionConfig,
    actionFormOptions,
    availableFormFields,
  }
}
