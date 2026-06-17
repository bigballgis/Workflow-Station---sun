import { describe, it, expect, beforeEach } from 'vitest'
import { parseBpmnDiagram } from '../bpmnDiagramParser'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

/**
 * Regression: on a COMPLETED task view, a multi-instance subprocess whose inner tasks have only
 * PENDING history records must NOT be painted completed (green). It is "entered" only because a
 * child task carries a history row, but the children have not actually run yet → it stays pending.
 */
const t = (key: string) => key

// submit → assignment → [multi: start → sub form1 → Route(XOR) → sub form2 → end] → End
const MI_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC">
  <process id="Process_1_KK">
    <startEvent id="StartEvent_1" name="Start"/>
    <userTask id="submit" name="submit"/>
    <userTask id="assignment" name="assignment"/>
    <subProcess id="multi" name="multi">
      <multiInstanceLoopCharacteristics/>
      <startEvent id="multi_start"/>
      <userTask id="subform1" name="sub form1"/>
      <exclusiveGateway id="Route" name="Route"/>
      <userTask id="subform2" name="sub form2"/>
      <endEvent id="multi_end"/>
      <sequenceFlow id="f_ms_s1" sourceRef="multi_start" targetRef="subform1"/>
      <sequenceFlow id="f_s1_r" sourceRef="subform1" targetRef="Route"/>
      <sequenceFlow id="f_r_s2" sourceRef="Route" targetRef="subform2"/>
      <sequenceFlow id="f_s2_e" sourceRef="subform2" targetRef="multi_end"/>
    </subProcess>
    <endEvent id="EndEvent_1" name="End"/>
    <sequenceFlow id="f0" sourceRef="StartEvent_1" targetRef="submit"/>
    <sequenceFlow id="f1" sourceRef="submit" targetRef="assignment"/>
    <sequenceFlow id="f2" sourceRef="assignment" targetRef="multi"/>
    <sequenceFlow id="f3" sourceRef="multi" targetRef="EndEvent_1"/>
  </process>
  <bpmndi:BPMNDiagram>
    <bpmndi:BPMNPlane>
      <bpmndi:BPMNShape bpmnElement="StartEvent_1"><dc:Bounds x="0" y="0" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="submit"><dc:Bounds x="80" y="0" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="assignment"><dc:Bounds x="220" y="0" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="multi"><dc:Bounds x="360" y="0" width="500" height="200"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="multi_start"><dc:Bounds x="380" y="60" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="subform1"><dc:Bounds x="450" y="40" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="Route"><dc:Bounds x="600" y="55" width="50" height="50"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="subform2"><dc:Bounds x="700" y="40" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="EndEvent_1"><dc:Bounds x="900" y="0" width="36" height="36"/></bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`

// Completed-task history (assignment view): submit/assignment done, sub form1 only PENDING.
const HISTORY_PENDING_MI = [
  { nodeName: 'submit', status: 'completed' },
  { nodeName: 'assignment', status: 'completed' },
  { nodeName: 'sub form1', status: 'pending' },
]

describe('parseBpmnDiagram — MI subprocess pending on completed view', () => {
  beforeEach(() => clearBpmnParseCache())

  const parse = () =>
    parseBpmnDiagram(MI_BPMN, {
      taskInfo: { taskName: 'assignment', taskDefinitionKey: 'assignment' },
      historyRecords: HISTORY_PENDING_MI,
      isCompletedTask: true,
      t,
    })!

  it('does not mark the MI subprocess completed when its children are only pending', () => {
    const r = parse()
    const multi = r.processNodes.find(n => n.id === 'multi')
    expect(multi?.status).toBe('pending')
    expect(r.completedNodeIds).not.toContain('multi')
  })

  it('does not mark the MI subprocess inner start completed', () => {
    const r = parse()
    const innerStart = r.processNodes.find(n => n.id === 'multi_start')
    expect(innerStart?.status).toBe('pending')
    expect(r.completedNodeIds).not.toContain('multi_start')
  })

  it('keeps the inner gateway pending (its source task has not completed)', () => {
    const r = parse()
    const route = r.processNodes.find(n => n.id === 'Route')
    expect(route?.status).toBe('pending')
  })

  it('still marks the executed outer tasks completed', () => {
    const r = parse()
    expect(r.processNodes.find(n => n.id === 'submit')?.status).toBe('completed')
    expect(r.processNodes.find(n => n.id === 'assignment')?.status).toBe('completed')
  })

  it('marks the MI subprocess completed when all its children are completed', () => {
    const r = parseBpmnDiagram(MI_BPMN, {
      taskInfo: { taskName: 'assignment', taskDefinitionKey: 'assignment' },
      historyRecords: [
        { nodeName: 'submit', status: 'completed' },
        { nodeName: 'assignment', status: 'completed' },
        { nodeName: 'sub form1', status: 'completed' },
        { nodeName: 'sub form2', status: 'completed' },
      ],
      isCompletedTask: true,
      t,
    })!
    expect(r.processNodes.find(n => n.id === 'multi')?.status).toBe('completed')
    expect(r.completedNodeIds).toContain('multi')
  })
})
