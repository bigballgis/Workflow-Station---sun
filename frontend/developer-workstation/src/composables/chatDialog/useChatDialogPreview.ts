import type { AiGeneratedData, GenerationPreviewData } from '@/types/aiGeneration'

/**
 * Pure helpers for the generation preview summary and degradation timestamps.
 *
 * Behavior is intentionally identical to the previous inline implementation in
 * ChatDialog.vue — no streaming/rendering side effects live here.
 */
export function useChatDialogPreview() {
  // Compute GenerationPreviewData from AiGeneratedData
  function computePreviewData(data: AiGeneratedData): GenerationPreviewData {
    const tables = data.tableDefinitions || []
    const forms = data.formDefinitions || []
    const actions = data.actionDefinitions || []
    const process = data.processDefinition

    let totalFieldCount = 0
    for (const table of tables) {
      totalFieldCount += (table.fieldDefinitions || []).length
    }

    let processNodeCount = 0
    let processGatewayCount = 0
    if (process?.bpmnXml) {
      // Simple counting from BPMN XML — supports both prefixed (bpmn:tag) and unprefixed (tag) forms
      const xml = process.bpmnXml as string
      const nodePattern = /<(?:bpmn:)?(?:userTask|serviceTask|scriptTask|startEvent|endEvent|task)\b/g
      const gatewayPattern = /<(?:bpmn:)?(?:exclusiveGateway|parallelGateway|inclusiveGateway|eventBasedGateway)\b/g
      const taskMatches = xml.match(nodePattern)
      processNodeCount = taskMatches ? taskMatches.length : 0
      const gatewayMatches = xml.match(gatewayPattern)
      processGatewayCount = gatewayMatches ? gatewayMatches.length : 0
    }

    const actionTypes = [...new Set(actions.map((a: any) => a.actionType).filter(Boolean))]

    return {
      tableCount: tables.length,
      totalFieldCount,
      formCount: forms.length,
      actionCount: actions.length,
      actionTypes,
      processNodeCount,
      processGatewayCount,
      decisionCount: data.decisionDefinitions?.length || 0,
      tableRelationCount: data.tableRelations?.length || 0,
      iconSvg: data.icon?.svgContent
    }
  }

  // Task 16.4: Degradation helpers
  function formatRelativeTime(isoTime: string): string {
    try {
      const diff = Date.now() - new Date(isoTime).getTime()
      const minutes = Math.floor(diff / 60000)
      if (minutes < 1) return '< 1 min ago'
      if (minutes < 60) return `${minutes} min ago`
      const hours = Math.floor(minutes / 60)
      return `${hours}h ago`
    } catch {
      return isoTime
    }
  }

  return {
    computePreviewData,
    formatRelativeTime
  }
}
