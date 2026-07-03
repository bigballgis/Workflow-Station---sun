import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailDiagramParser } from '../useApplicationDetailDiagramParser'
import type { ApplicationDetailCtx } from '../context'
import type { HistoryRecord } from '@/types/historyRecord'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://test">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="Start_1" />
    <bpmn:userTask id="UserTask_Submit" name="Submit" />
    <bpmn:userTask id="UserTask_Approve1" name="Approve1" />
    <bpmn:endEvent id="End_1" />
    <bpmn:sequenceFlow id="f1" sourceRef="Start_1" targetRef="UserTask_Submit" />
    <bpmn:sequenceFlow id="f2" sourceRef="UserTask_Submit" targetRef="UserTask_Approve1" />
    <bpmn:sequenceFlow id="f3" sourceRef="UserTask_Approve1" targetRef="End_1" />
  </bpmn:process>
</bpmn:definitions>`

function makeCtx(historyRecords: HistoryRecord[], currentNode: string) {
  const ctx = {
    t: (key: string) => key,
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

const record = (nodeId: string, nodeName: string, status: HistoryRecord['status'], action?: HistoryRecord['action']): HistoryRecord => ({
  id: `h_${nodeId}_${status}_${action ?? ''}`,
  nodeId,
  nodeName,
  status,
  action,
  createdTime: '',
})

describe('application detail diagram parser — draft return', () => {
  it('resets previously completed downstream stages to pending after draft', () => {
    clearBpmnParseCache()
    const ctx = makeCtx([
      record('Start_1', 'Submit application', 'completed', 'submit'),
      record('UserTask_Submit', 'Submit', 'completed', 'submit'),
      record('UserTask_Approve1', 'Approve1', 'completed', 'draft'),
      record('UserTask_Submit', 'Submit', 'current'),
    ], 'Submit')
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('UserTask_Submit')?.status).toBe('current')
    expect(byId.get('UserTask_Approve1')?.status).toBe('pending')
    expect(byId.get('Start_1')?.status).toBe('completed')
    expect(byId.get('End_1')?.status).toBe('pending')
    expect(ctx.completedNodeIds.value).not.toContain('UserTask_Approve1')
  })

  it('keeps normal running rendering when no draft return happened', () => {
    clearBpmnParseCache()
    const ctx = makeCtx([
      record('Start_1', 'Submit application', 'completed', 'submit'),
      record('UserTask_Submit', 'Submit', 'completed', 'submit'),
      record('UserTask_Approve1', 'Approve1', 'current'),
    ], 'Approve1')
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('UserTask_Submit')?.status).toBe('completed')
    expect(byId.get('UserTask_Approve1')?.status).toBe('current')
  })
})
