import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ProcessNode, ProcessFlow } from '@/components/ProcessDiagram.vue'
import type { DiagramStatusSuppressMode } from '@/utils/bpmnDiagramDraftReturn'
import { parseBpmnFormId, parsePreviousFormIds } from './bpmnFormIdParser'
import { parseBpmnDiagram } from './bpmnDiagramParser'

export function useBpmnParser(options: {
  taskInfo: Ref<Record<string, any>>
  historyRecords: Ref<any[]>
  isCompletedTask: Ref<boolean>
  /** When RETURN_TO_REQUESTER, diagram stays neutral (no completed/current colors). */
  processState?: Ref<string | undefined>
}) {
  const { t } = useI18n()

  const processNodes = ref<ProcessNode[]>([])
  const processFlows = ref<ProcessFlow[]>([])
  const completedNodeIds = ref<string[]>([])
  const currentNodeId = ref('')
  const bpmnXml = ref('')
  const diagramSuppressMode = ref<DiagramStatusSuppressMode>('none')

  function parseBpmnXmlAndGetFormId(xml: string): { formId: string | null; formName: string | null; readOnly: boolean } {
    return parseBpmnFormId(
      xml,
      options.taskInfo.value.taskDefinitionKey || '',
      options.taskInfo.value.taskName || '',
    )
  }

  function parseBpmnXmlAndGetPreviousFormIds(xml: string): Array<{ formId: string | null; formName: string | null; taskName: string | null }> {
    return parsePreviousFormIds(
      xml,
      options.taskInfo.value.taskDefinitionKey || '',
      options.taskInfo.value.taskName || '',
    )
  }

  function parseBpmnXml(xml: string) {
    if (!xml) return
    // Reset before parse — matches the original ordering: empty xml returns early (no reset),
    // an unparseable/erroring doc still clears the current node.
    currentNodeId.value = ''
    const result = parseBpmnDiagram(xml, {
      taskInfo: options.taskInfo.value,
      historyRecords: options.historyRecords.value,
      isCompletedTask: options.isCompletedTask.value,
      processState: options.processState?.value,
      t,
    })
    if (!result) return
    diagramSuppressMode.value = result.diagramSuppressMode
    processFlows.value = result.processFlows
    processNodes.value = result.processNodes
    completedNodeIds.value = result.completedNodeIds
    currentNodeId.value = result.currentNodeId
  }

  return {
    processNodes,
    processFlows,
    completedNodeIds,
    currentNodeId,
    bpmnXml,
    diagramSuppressMode,
    parseBpmnXml,
    parseBpmnXmlAndGetFormId,
    parseBpmnXmlAndGetPreviousFormIds,
  }
}
