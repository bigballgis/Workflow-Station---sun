import { describe, it, expect, beforeEach } from 'vitest'
import { parseBpmnDiagram } from '../bpmnDiagramParser'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

const t = (key: string) => key

const SEND_TASK_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <process id="Process_1">
    <startEvent id="Start_1" name="Start"/>
    <userTask id="UserTask_Submit" name="Case Submission"/>
    <sendTask id="SendTask_Email" name="Send Task"/>
    <userTask id="UserTask_Assign" name="Transaction Assignment"/>
    <endEvent id="End_1" name="End"/>
    <sequenceFlow id="f1" sourceRef="Start_1" targetRef="UserTask_Submit"/>
    <sequenceFlow id="f2" sourceRef="UserTask_Submit" targetRef="SendTask_Email"/>
    <sequenceFlow id="f3" sourceRef="SendTask_Email" targetRef="UserTask_Assign"/>
    <sequenceFlow id="f4" sourceRef="UserTask_Assign" targetRef="End_1"/>
  </process>
</definitions>`

describe('parseBpmnDiagram — sendTask status', () => {
  beforeEach(() => clearBpmnParseCache())

  it('marks sendTask completed when SEND history is present', () => {
    const r = parseBpmnDiagram(SEND_TASK_BPMN, {
      taskInfo: { taskName: 'Transaction Assignment', taskDefinitionKey: 'UserTask_Assign' },
      historyRecords: [
        { nodeId: 'UserTask_Submit', nodeName: 'Case Submission', status: 'completed' },
        { nodeId: 'SendTask_Email', nodeName: 'Send Task', status: 'completed' },
        { nodeId: 'UserTask_Assign', nodeName: 'Transaction Assignment', status: 'current' },
      ],
      isCompletedTask: false,
      t,
    })!

    expect(r.processNodes.find(n => n.id === 'SendTask_Email')?.status).toBe('completed')
    expect(r.completedNodeIds).toContain('SendTask_Email')
    expect(r.processNodes.find(n => n.id === 'UserTask_Assign')?.status).toBe('current')
  })

  it('marks completed sendTask and taken end event green on a completed-task snapshot', () => {
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <process id="Process_EmailInboundReply">
    <userTask id="Task_ConfirmSend" name="Confirm &amp; Send"/>
    <exclusiveGateway id="Gateway_NeedReply" name="Need Reply?"/>
    <sendTask id="Task_SendReply" name="Send Reply Email"/>
    <endEvent id="EndEvent_Sent" name="Sent"/>
    <endEvent id="EndEvent_Skipped" name="Skipped"/>
    <sequenceFlow id="Flow_3" sourceRef="Task_ConfirmSend" targetRef="Gateway_NeedReply"/>
    <sequenceFlow id="Flow_Send" sourceRef="Gateway_NeedReply" targetRef="Task_SendReply"/>
    <sequenceFlow id="Flow_Skip" sourceRef="Gateway_NeedReply" targetRef="EndEvent_Skipped"/>
    <sequenceFlow id="Flow_4" sourceRef="Task_SendReply" targetRef="EndEvent_Sent"/>
  </process>
</definitions>`
    const r = parseBpmnDiagram(xml, {
      taskInfo: { taskName: 'Confirm & Send', taskDefinitionKey: 'Task_ConfirmSend' },
      historyRecords: [
        { nodeId: 'Task_ConfirmSend', nodeName: 'Confirm & Send', status: 'completed', action: 'approve' },
        { nodeId: 'Gateway_NeedReply', nodeName: 'Need Reply?', status: 'completed', action: 'approve' },
        { nodeId: 'Task_SendReply', nodeName: 'Send Reply Email', status: 'completed', action: 'send' },
        { nodeId: 'EndEvent_Sent', nodeName: 'Sent', status: 'completed' },
      ],
      isCompletedTask: true,
      t,
    })!

    expect(r.processNodes.find(n => n.id === 'Task_SendReply')?.status).toBe('completed')
    expect(r.processNodes.find(n => n.id === 'EndEvent_Sent')?.status).toBe('completed')
    expect(r.processNodes.find(n => n.id === 'EndEvent_Skipped')?.status).toBe('pending')
    expect(r.completedNodeIds).toContain('Task_SendReply')
    expect(r.completedNodeIds).toContain('EndEvent_Sent')
  })
})
