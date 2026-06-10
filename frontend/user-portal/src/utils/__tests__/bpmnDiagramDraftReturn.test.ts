import { describe, expect, it } from 'vitest'
import {
  applyDraftReturnDiagramStatus,
  resolveDiagramStatusSuppressMode,
} from '../bpmnDiagramDraftReturn'
import type { ProcessNode } from '@/components/ProcessDiagram.vue'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

function bpmn(processBody: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://test">
  <bpmn:process id="Process_1" isExecutable="true">
    ${processBody}
  </bpmn:process>
</bpmn:definitions>`
}

const xml = bpmn(`
  <bpmn:startEvent id="Start_1" />
  <bpmn:userTask id="UserTask_Submit" name="Submit" />
  <bpmn:userTask id="UserTask_Approve1" name="Approve1" />
  <bpmn:endEvent id="End_1" />
  <bpmn:sequenceFlow id="f1" sourceRef="Start_1" targetRef="UserTask_Submit" />
  <bpmn:sequenceFlow id="f2" sourceRef="UserTask_Submit" targetRef="UserTask_Approve1" />
  <bpmn:sequenceFlow id="f3" sourceRef="UserTask_Approve1" targetRef="End_1" />
`)

describe('bpmnDiagramDraftReturn', () => {
  it('detects draft-return when on first step with downstream completed history', () => {
    clearBpmnParseCache()
    const mode = resolveDiagramStatusSuppressMode(xml, {
      currentTaskName: 'Submit',
      historyRecords: [
        { nodeName: 'Submit', status: 'completed', action: 'approve' },
        { nodeName: 'Approve1', status: 'completed', action: 'approve' },
        { nodeName: 'Approve1', status: 'completed', action: 'draft' },
      ],
    })
    expect(mode).toBe('draft-return')
  })

  it('keeps upstream green and downstream pending on draft-return', () => {
    clearBpmnParseCache()
    const nodes: ProcessNode[] = [
      { id: 'Start_1', name: 'Start', type: 'start', status: 'completed' },
      { id: 'UserTask_Submit', name: 'Submit', type: 'task', status: 'current' },
      { id: 'UserTask_Approve1', name: 'Approve1', type: 'task', status: 'completed' },
    ]
    const result = applyDraftReturnDiagramStatus(nodes, xml, 'UserTask_Submit')
    expect(result.nodes.find(n => n.id === 'Start_1')?.status).toBe('completed')
    expect(result.nodes.find(n => n.id === 'UserTask_Submit')?.status).toBe('current')
    expect(result.nodes.find(n => n.id === 'UserTask_Approve1')?.status).toBe('pending')
    expect(result.completedNodeIds).toContain('Start_1')
  })

  it('resolves anchor from current node when currentNodeId ref is empty', () => {
    clearBpmnParseCache()
    const nodes: ProcessNode[] = [
      { id: 'Start_1', name: 'Start', type: 'start', status: 'pending' },
      { id: 'UserTask_Submit', name: 'Submit', type: 'task', status: 'current' },
      { id: 'UserTask_Approve1', name: 'Approve1', type: 'task', status: 'completed' },
    ]
    const result = applyDraftReturnDiagramStatus(nodes, xml, '')
    expect(result.nodes.find(n => n.id === 'Start_1')?.status).toBe('completed')
    expect(result.nodes.find(n => n.id === 'UserTask_Approve1')?.status).toBe('pending')
  })
})
