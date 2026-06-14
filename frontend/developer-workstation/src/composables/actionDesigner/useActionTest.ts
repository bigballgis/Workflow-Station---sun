import { ref } from 'vue'
import type { Ref } from 'vue'
import { functionUnitApi, type ActionDefinition } from '@/api/functionUnit'

interface UseActionTestOptions {
  functionUnitId: number
  selectedAction: Ref<ActionDefinition | null>
  t: (key: string, params?: Record<string, unknown>) => string
}

/**
 * 动作测试对话框相关状态与方法：触发测试、执行测试请求，
 * 以及 N8N 结构化输入的初始化。
 */
export function useActionTest(options: UseActionTestOptions) {
  const { functionUnitId, selectedAction, t } = options

  const showTestDialog = ref(false)
  const testData = ref('{}')
  const testResult = ref('')
  const testing = ref(false)
  const testRawJsonMode = ref(false)
  const testActionType = ref('')
  const testInputMapping = ref<Array<{ paramName: string; paramLabel: string; paramType: string; required: boolean }>>([])
  const testStructuredData = ref<Record<string, any>>({})

  function handleTestAction(row: ActionDefinition) {
    selectedAction.value = row
    testData.value = '{}'
    testResult.value = ''
    testRawJsonMode.value = false
    testActionType.value = row.actionType
    // Auto-generate structured input fields for N8N_ACTION
    if (row.actionType === 'N8N_ACTION' && row.configJson?.inputMapping) {
      testInputMapping.value = row.configJson.inputMapping
      const initial: Record<string, any> = {}
      for (const param of row.configJson.inputMapping) {
        if (param.paramType === 'number') initial[param.paramName] = 0
        else if (param.paramType === 'boolean') initial[param.paramName] = false
        else initial[param.paramName] = ''
      }
      testStructuredData.value = initial
    } else {
      testInputMapping.value = []
      testStructuredData.value = {}
    }
    showTestDialog.value = true
  }

  async function executeTest() {
    if (!selectedAction.value) return
    testing.value = true
    try {
      let data: Record<string, unknown>
      if (testActionType.value === 'N8N_ACTION' && testInputMapping.value.length > 0 && !testRawJsonMode.value) {
        data = { ...testStructuredData.value }
      } else {
        data = JSON.parse(testData.value)
      }
      const res = await functionUnitApi.testAction?.(functionUnitId, selectedAction.value.id, data)
      testResult.value = JSON.stringify(res?.data || {}, null, 2)
    } catch (e: any) {
      testResult.value = `Error: ${e.message || t('action.testFailed')}`
    } finally {
      testing.value = false
    }
  }

  return {
    showTestDialog,
    testData,
    testResult,
    testing,
    testRawJsonMode,
    testActionType,
    testInputMapping,
    testStructuredData,
    handleTestAction,
    executeTest,
  }
}
