import { ref } from 'vue'
import { n8nApi, type N8nConfig, type N8nWorkflow } from '@/api/n8n'

interface UseN8nActionOptions {
  actionConfig: Record<string, any>
}

/**
 * N8N Action 配置相关状态与方法：配置/工作流列表加载、
 * 选择联动以及输入/输出参数映射的增删。
 */
export function useN8nAction(options: UseN8nActionOptions) {
  const { actionConfig } = options

  // N8N Action 相关状态
  const n8nConfigList = ref<N8nConfig[]>([])
  const n8nWorkflowList = ref<N8nWorkflow[]>([])

  // ===== N8N Action helper methods =====

  /** Load N8N connection configs from admin-center */
  async function loadN8nConfigs() {
    try {
      n8nConfigList.value = await n8nApi.getConfigs()
    } catch {
      n8nConfigList.value = []
    }
  }

  /** Load N8N workflows for selected config */
  async function loadN8nWorkflows(configId: string) {
    if (!configId) {
      n8nWorkflowList.value = []
      return
    }
    try {
      n8nWorkflowList.value = await n8nApi.getWorkflows(configId)
    } catch {
      n8nWorkflowList.value = []
    }
  }

  /** Handle N8N config selection change */
  function onN8nConfigChange(configId: string) {
    actionConfig.n8nWorkflowId = ''
    actionConfig.webhookUrl = ''
    n8nWorkflowList.value = []
    loadN8nWorkflows(configId)
  }

  /** Handle N8N workflow selection change - auto-fill webhook URL */
  function onN8nWorkflowChange(workflowId: string) {
    const selected = n8nWorkflowList.value.find(wf => wf.id === workflowId)
    if (selected?.webhookUrl) {
      actionConfig.webhookUrl = selected.webhookUrl
    }
  }

  /** Add input parameter mapping row */
  function addN8nInputParam() {
    if (!actionConfig.inputMapping) actionConfig.inputMapping = []
    actionConfig.inputMapping.push({ paramName: '', paramLabel: '', paramType: 'string', required: false })
  }

  /** Remove input parameter mapping row */
  function removeN8nInputParam(index: number) {
    actionConfig.inputMapping.splice(index, 1)
  }

  /** Add output result mapping row */
  function addN8nOutputMapping() {
    if (!actionConfig.outputMapping) actionConfig.outputMapping = []
    actionConfig.outputMapping.push({ source: '', target: '' })
  }

  /** Remove output result mapping row */
  function removeN8nOutputMapping(index: number) {
    actionConfig.outputMapping.splice(index, 1)
  }

  return {
    n8nConfigList,
    n8nWorkflowList,
    loadN8nConfigs,
    loadN8nWorkflows,
    onN8nConfigChange,
    onN8nWorkflowChange,
    addN8nInputParam,
    removeN8nInputParam,
    addN8nOutputMapping,
    removeN8nOutputMapping,
  }
}
