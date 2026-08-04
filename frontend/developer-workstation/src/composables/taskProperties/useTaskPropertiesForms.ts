/**
 * 通用 Task 节点属性面板的表单绑定逻辑。
 *
 * 托管 forms 列表、loadForms 加载与 handleFormChange 处理，行为与拆分前逐字一致。
 * 通过 wrapper 闭包接收状态依赖（formId/updateExtProp），避免与状态 composable
 * 形成循环依赖。
 */
import { ref } from 'vue'
import type { Ref } from 'vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import type { FormDefinition } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import { connectionApi, type EmailConnection } from '@/api/connection'
import { emailTemplateApi, type EmailTemplate } from '@/api/emailTemplate'

/** 透传 props 的响应式访问器（与 SFC 内 reactive 适配器结构一致） */
export interface TaskPropertiesAccessor {
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId: number
}

/** 表单逻辑所需的状态依赖 */
export interface TaskPropertiesFormsDeps {
  formId: Ref<number | null>
  updateExtProp: (name: string, value: any) => void
}

export function useTaskPropertiesForms(
  props: TaskPropertiesAccessor,
  deps: TaskPropertiesFormsDeps
) {
  const forms = ref<FormDefinition[]>([])
  const emailConnections = ref<EmailConnection[]>([])
  const emailTemplates = ref<EmailTemplate[]>([])

  function handleFormChange(id: number | null) {
    deps.updateExtProp('formId', id)
    const form = forms.value.find(f => f.id === id)
    if (form) {
      deps.updateExtProp('formName', form.formName)
    }
  }

  async function loadForms() {
    try {
      const res = await functionUnitApi.getForms(props.functionUnitId)
      forms.value = res.data || []
    } catch {
      forms.value = []
    }
  }

  async function loadEmailConnections() {
    try {
      const res = await connectionApi.list(props.functionUnitId)
      emailConnections.value = (res.data || []).filter(c => {
        const direction = c.direction || 'OUTBOUND'
        return direction === 'OUTBOUND' || direction === 'BOTH'
      })
    } catch {
      emailConnections.value = []
    }
  }

  async function loadEmailTemplates() {
    try {
      const res = await emailTemplateApi.list(props.functionUnitId)
      emailTemplates.value = (res.data || []).filter(t => t.enabled)
    } catch {
      emailTemplates.value = []
    }
  }

  return {
    forms,
    emailConnections,
    emailTemplates,
    handleFormChange,
    loadForms,
    loadEmailConnections,
    loadEmailTemplates
  }
}
