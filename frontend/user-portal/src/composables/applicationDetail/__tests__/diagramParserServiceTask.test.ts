import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailDiagramParser } from '../useApplicationDetailDiagramParser'
import { getHistoryAction, getHistoryStatus } from '../subTableRowHelpers'
import type { ApplicationDetailCtx } from '../context'
import type { HistoryRecord } from '@/types/historyRecord'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

/**
 * Engine history reports non-email service tasks (e.g. Activepieces) as operationType=AUTO.
 * Only the branch that actually ran turns green; the join gateway follows it.
 */
const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://test">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="Start_1" />
    <bpmn:userTask id="UserTask_Submit" name="Submit PDF" />
    <bpmn:exclusiveGateway id="Gw_Split" name="Extraction provider" />
    <bpmn:serviceTask id="Svc_A" name="GAIP extraction" />
    <bpmn:serviceTask id="Svc_B" name="Content Organizer extraction" />
    <bpmn:exclusiveGateway id="Gw_Join" />
    <bpmn:userTask id="UserTask_Review" name="Review company information" />
    <bpmn:endEvent id="End_1" />
    <bpmn:sequenceFlow id="f1" sourceRef="Start_1" targetRef="UserTask_Submit" />
    <bpmn:sequenceFlow id="f2" sourceRef="UserTask_Submit" targetRef="Gw_Split" />
    <bpmn:sequenceFlow id="f3" sourceRef="Gw_Split" targetRef="Svc_A" />
    <bpmn:sequenceFlow id="f4" sourceRef="Gw_Split" targetRef="Svc_B" />
    <bpmn:sequenceFlow id="f5" sourceRef="Svc_A" targetRef="Gw_Join" />
    <bpmn:sequenceFlow id="f6" sourceRef="Svc_B" targetRef="Gw_Join" />
    <bpmn:sequenceFlow id="f7" sourceRef="Gw_Join" targetRef="UserTask_Review" />
    <bpmn:sequenceFlow id="f8" sourceRef="UserTask_Review" targetRef="End_1" />
  </bpmn:process>
</bpmn:definitions>`

const engineRow = (activityId: string, activityName: string, operationType: string): HistoryRecord => ({
  id: `h_${activityId}`,
  nodeId: activityId,
  nodeName: activityName,
  status: getHistoryStatus(operationType),
  action: getHistoryAction(operationType),
  createdTime: '',
})

describe('application detail diagram parser — service task status', () => {
  it('marks the executed service task and join gateway completed, skipped branch pending', () => {
    clearBpmnParseCache()
    const ctx = {
      t: (key: string) => key,
      snapshotTaskName: '',
      snapshotTaskDefinitionKey: '',
      processInfo: ref({ status: 'RUNNING', currentNode: 'Review company information' }),
      processNodes: ref([]),
      processFlows: ref([]),
      currentNodeId: ref(''),
      completedNodeIds: ref<string[]>([]),
      diagramReady: ref(false),
      historyRecords: ref([
        engineRow('Start_1', '', 'SUBMIT'),
        engineRow('UserTask_Submit', 'Submit PDF', 'APPROVE'),
        engineRow('Gw_Split', 'Extraction provider', 'GATEWAY'),
        engineRow('Svc_A', 'GAIP extraction', 'AUTO'),
        engineRow('Gw_Join', '', 'GATEWAY'),
        engineRow('UserTask_Review', 'Review company information', 'PENDING'),
      ]),
      snapshotActivityId: ref(''),
      hasIncompleteMiRows: () => false,
      hasCompletedMiRows: () => false,
      diagramParseScheduled: false,
    } as unknown as ApplicationDetailCtx
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('Svc_A')?.status).toBe('completed')
    expect(byId.get('Svc_B')?.status).toBe('pending')
    expect(byId.get('Gw_Join')?.status).toBe('completed')
    expect(byId.get('UserTask_Review')?.status).toBe('current')
  })
})
