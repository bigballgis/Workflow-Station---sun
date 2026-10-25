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

  const defaults = () => ({
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
    postEmailRules: [] as ActionPostEmailRule[],
    subActions: [] as Array<string | number>,
    executionOrder: 'sequential',
    allowedFromStatus: [] as string[],
  })

  const actionConfig = reactive<Record<string, unknown>>(defaults())

  watch(selectedAction, (action) => {
    const config = JSON.parse(JSON.stringify(action?.configJson ?? {})) as Record<string, unknown>
    for (const key of Object.keys(actionConfig)) delete actionConfig[key]
    Object.assign(actionConfig, defaults(), config)
    actionConfig.postEmailRules = normalizePostEmailRules(config)
    stripLegacyPostEmail(actionConfig)

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
