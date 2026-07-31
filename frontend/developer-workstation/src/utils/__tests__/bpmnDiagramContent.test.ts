import { describe, expect, it } from 'vitest'
import { inspectBpmnDiagram, isEmptyBpmnDiagram } from '@/utils/bpmnDiagramContent'

const NON_EMPTY_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1">
  <bpmn:process id="Process_50030" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start" />
    <bpmn:serviceTask id="Activity_1" name="Call AP" />
    <bpmn:endEvent id="EndEvent_1" name="End" />
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_50030">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

/** 误触快捷键清空画布后 bpmn-js 导出的形状：process 与 plane 都还在，但里面什么都没有。 */
const WIPED_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" id="Definitions_1">
  <bpmn:process id="Process_50030" isExecutable="true" />
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_50030" />
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

describe('bpmnDiagramContent', () => {
  it('counts flow nodes and DI shapes, ignoring sequence flows', () => {
    expect(inspectBpmnDiagram(NON_EMPTY_XML)).toEqual({
      flowNodeCount: 3,
      shapeCount: 1,
      unparsable: false
    })
    expect(isEmptyBpmnDiagram(NON_EMPTY_XML)).toBe(false)
  })

  it('treats a wiped canvas (process + plane, no children) as empty', () => {
    expect(inspectBpmnDiagram(WIPED_XML)).toEqual({
      flowNodeCount: 0,
      shapeCount: 0,
      unparsable: false
    })
    expect(isEmptyBpmnDiagram(WIPED_XML)).toBe(true)
  })

  it('treats missing / blank XML as empty (no previously saved diagram)', () => {
    expect(isEmptyBpmnDiagram(undefined)).toBe(true)
    expect(isEmptyBpmnDiagram(null)).toBe(true)
    expect(isEmptyBpmnDiagram('   ')).toBe(true)
  })

  it('recognises nodes without a namespace prefix and without DI', () => {
    const noPrefix = `<definitions><process id="P"><userTask id="T" /></process></definitions>`
    expect(inspectBpmnDiagram(noPrefix).flowNodeCount).toBe(1)
    expect(isEmptyBpmnDiagram(noPrefix)).toBe(false)
  })

  it('does not claim an unparsable string is an empty diagram', () => {
    const broken = '<bpmn:definitions><bpmn:process id="P"'
    expect(inspectBpmnDiagram(broken).unparsable).toBe(true)
    expect(isEmptyBpmnDiagram(broken)).toBe(false)
  })
})
