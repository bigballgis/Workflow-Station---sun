import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailDiagramParser } from '../useApplicationDetailDiagramParser'
import type { ApplicationDetailCtx } from '../context'
import type { HistoryRecord } from '@/types/historyRecord'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

/**
 * Designer BPMN keeps sendTask; deploy converts email sendTask → serviceTask in Flowable.
 * Portal diagram parse must still color the designer sendTask from SEND history.
 */
const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://test">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="Start_1" />
    <bpmn:userTask id="UserTask_Submit" name="Case Submission" />
    <bpmn:sendTask id="SendTask_Email" name="Send Task" />
    <bpmn:userTask id="UserTask_Assign" name="Transaction Assignment" />
    <bpmn:endEvent id="End_1" />
    <bpmn:sequenceFlow id="f1" sourceRef="Start_1" targetRef="UserTask_Submit" />
    <bpmn:sequenceFlow id="f2" sourceRef="UserTask_Submit" targetRef="SendTask_Email" />
    <bpmn:sequenceFlow id="f3" sourceRef="SendTask_Email" targetRef="UserTask_Assign" />
    <bpmn:sequenceFlow id="f4" sourceRef="UserTask_Assign" targetRef="End_1" />
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

describe('application detail diagram parser — sendTask status', () => {
  it('marks completed sendTask green when SEND history exists (by name)', () => {
    clearBpmnParseCache()
    const ctx = makeCtx([
      record('Start_1', 'Submit application', 'completed', 'submit'),
      record('UserTask_Submit', 'Case Submission', 'completed', 'approve'),
      record('SendTask_Email', 'Send Task', 'completed', 'send'),
      record('UserTask_Assign', 'Transaction Assignment', 'current'),
    ], 'Transaction Assignment')
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('UserTask_Submit')?.status).toBe('completed')
    expect(byId.get('SendTask_Email')?.status).toBe('completed')
    expect(byId.get('UserTask_Assign')?.status).toBe('current')
    expect(ctx.completedNodeIds.value).toContain('SendTask_Email')
  })

  it('marks completed sendTask green when history matches activity id only', () => {
    clearBpmnParseCache()
    const ctx = makeCtx([
      record('UserTask_Submit', 'Case Submission', 'completed', 'approve'),
      // Engine may keep activity id from converted serviceTask while designer XML is sendTask
      record('SendTask_Email', '', 'completed', 'send'),
      record('UserTask_Assign', 'Transaction Assignment', 'current'),
    ], 'Transaction Assignment')
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('SendTask_Email')?.status).toBe('completed')
    expect(ctx.completedNodeIds.value).toContain('SendTask_Email')
  })

  it('keeps pending sendTask grey when email has not run yet', () => {
    clearBpmnParseCache()
    const ctx = makeCtx([
      record('UserTask_Submit', 'Case Submission', 'current'),
    ], 'Case Submission')
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('SendTask_Email')?.status).toBe('pending')
    expect(ctx.completedNodeIds.value).not.toContain('SendTask_Email')
  })
})
