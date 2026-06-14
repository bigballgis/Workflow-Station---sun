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

  return {
    forms,
    handleFormChange,
    loadForms
  }
}
