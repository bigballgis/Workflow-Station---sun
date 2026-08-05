import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailDiagramParser } from '../useApplicationDetailDiagramParser'
import type { ApplicationDetailCtx } from '../context'
import type { HistoryRecord } from '@/types/historyRecord'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://test">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start" />
    <bpmn:task id="Activity_0bydtjc" name="test" />
    <bpmn:endEvent id="EndEvent_1" name="End" />
    <bpmn:sequenceFlow id="f1" sourceRef="StartEvent_1" targetRef="Activity_0bydtjc" />
    <bpmn:sequenceFlow id="f2" sourceRef="Activity_0bydtjc" targetRef="EndEvent_1" />
  </bpmn:process>
</bpmn:definitions>`

function makeCtx(
  historyRecords: HistoryRecord[],
  status: string,
  currentNode = '',
) {
  return {
    t: (key: string) => key,
    snapshotTaskName: '',
    snapshotTaskDefinitionKey: '',
    processInfo: ref({ status, currentNode }),
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
  } as unknown as ApplicationDetailCtx
}

describe('application detail diagram parser — generic bpmn:task with history', () => {
  it('marks generic task completed when history includes manualTask activity', () => {
    clearBpmnParseCache()
    const ctx = makeCtx(
      [
        {
          id: 'h1',
          nodeId: 'StartEvent_1',
          nodeName: 'Start',
          status: 'completed',
          action: 'submit',
          createdTime: '',
        },
        {
          id: 'h2',
          nodeId: 'Activity_0bydtjc',
          nodeName: 'test',
          status: 'completed',
          action: 'approve',
          createdTime: '',
        },
        {
          id: 'h3',
          nodeId: 'EndEvent_1',
          nodeName: 'End',
          status: 'completed',
          action: 'approve',
          createdTime: '',
        },
      ],
      'COMPLETED',
    )
    const { parseBpmnXml } = createApplicationDetailDiagramParser(ctx)
    parseBpmnXml(xml)

    const byId = new Map(ctx.processNodes.value.map(n => [n.id, n]))
    expect(byId.get('Activity_0bydtjc')?.status).toBe('completed')
    expect(byId.get('EndEvent_1')?.status).toBe('completed')
    expect(ctx.completedNodeIds.value).toContain('Activity_0bydtjc')
  })
})
