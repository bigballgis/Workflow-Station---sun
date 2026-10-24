import { reactive, computed, watch } from 'vue'
import type { Ref } from 'vue'
import type { ActionDefinition } from '@/api/functionUnit'
import type { ConditionExpression } from '@/components/designer/formBusinessLogicTypes'
import {
  normalizePostEmailRules,
  stripLegacyPostEmail,
  type ActionPostEmailRule,
} from '@/utils/actionPostEmailRules'

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
  loadActionBinding: (actionId: string | number) => void
}

export function useActionConfig(options: UseActionConfigOptions) {
  const { selectedAction, store, loadActionBinding } = options

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

  const actionConfig = reactive<Record<string, unknown>>({
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
    visibilityCondition: null as ConditionExpression[] | null,
    allowedRoles: [] as string[],
    sortOrder: 0,
    postEmailRules: [] as ActionPostEmailRule[],
  })

  watch(selectedAction, (action) => {
    if (action?.configJson) {
      Object.assign(actionConfig, JSON.parse(JSON.stringify(action.configJson)))
      const normalized = normalizePostEmailRules(JSON.parse(JSON.stringify(action.configJson)) as Record<string, unknown>)
      actionConfig.postEmailRules = normalized
      stripLegacyPostEmail(actionConfig)
    } else {
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
        visibilityCondition: null as ConditionExpression[] | null,
        allowedRoles: [] as string[],
        sortOrder: 0,
        postEmailRules: [] as ActionPostEmailRule[],
      })
    }

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
