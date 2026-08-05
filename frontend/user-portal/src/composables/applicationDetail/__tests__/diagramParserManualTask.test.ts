import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailDiagramParser } from '../useApplicationDetailDiagramParser'
import type { ApplicationDetailCtx } from '../context'
import type { HistoryRecord } from '@/types/historyRecord'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://test">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="Start_1" name="Start" />
    <bpmn:manualTask id="ManualTask_test" name="test" />
    <bpmn:endEvent id="End_1" name="End" />
    <bpmn:sequenceFlow id="f1" sourceRef="Start_1" targetRef="ManualTask_test" />
    <bpmn:sequenceFlow id="f2" sourceRef="ManualTask_test" targetRef="End_1" />
  </bpmn:process>
</bpmn:definitions>`

function makeCtx(historyRecords: HistoryRecord[], currentNode: string) {
  const ctx = {
    t: (key: string, named?: Record<string, unknown>) =>
      named ? `${key}:${JSON.stringify(named)}` : key,
    snapshotTaskName: '',
    snapshotTaskDefinitionKey: '',
    processInfo: ref({ status: 'RUNNING', currentNode }),
    processNodes: ref([]),
    processFlows: ref([]),
    currentNodeId: ref(''),
    completedNodeIds: ref<string[]>([]),
    diagramReady: ref(false),
    historyRecords: ref(historyRecords),
    snapshotActivityId: ref(''),
    hasIncompleteMiRows: () => false,
    hasCompletedMiRows: () => false,
    diagramParseScheduled: false,
  }
  return ctx as unknown as ApplicationDetailCtx
}

const record = (
  nodeId: string,
  nodeName: string,
  status: HistoryRecord['status'],
  action?: HistoryRecord['action'],
): HistoryRecord => ({
  id: `h_${nodeId}_${status}_${action ?? ''}`,
  nodeId,
  nodeName,
  status,
  action,
  createdTime: '',
})

describe('application detail diagram parser — manualTask status', () => {
  it('marks current manualTask orange when currentNode matches name', () => {
    clearBpmnParseCache()
    const ctx = makeCtx(
      [record('Start_1', 'Start', 'completed', 'submit')],
      'test',
    )
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('ManualTask_test')).toBeDefined()
    expect(byId.get('ManualTask_test')?.status).toBe('current')
    expect(ctx.currentNodeId.value).toBe('ManualTask_test')
    expect(byId.get('Start_1')?.status).toBe('completed')
    expect(byId.get('End_1')?.status).toBe('pending')
  })

  it('marks current manualTask from history when currentNode is empty', () => {
    clearBpmnParseCache()
    const ctx = makeCtx(
      [
        record('Start_1', 'Start', 'completed', 'submit'),
        record('ManualTask_test', 'test', 'current'),
      ],
      '',
    )
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('ManualTask_test')?.status).toBe('current')
  })

  it('marks completed manualTask green from history', () => {
    clearBpmnParseCache()
    const ctx = makeCtx(
      [
        record('Start_1', 'Start', 'completed', 'submit'),
        record('ManualTask_test', 'test', 'completed', 'approve'),
      ],
      '',
    )
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('ManualTask_test')?.status).toBe('completed')
    expect(ctx.completedNodeIds.value).toContain('ManualTask_test')
  })
})
