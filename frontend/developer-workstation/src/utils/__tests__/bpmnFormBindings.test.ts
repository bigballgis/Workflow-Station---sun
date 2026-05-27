import { describe, it, expect } from 'vitest'
import { parseBpmnNodeFormBindings, lookupNodeFormBinding } from '@/utils/bpmnFormBindings'

const SAMPLE_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:custom="http://workflow.platform/schema/custom"
  targetNamespace="http://workflow-station/test">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="Start_1" name="Start" />
    <bpmn:userTask id="Task_1" name="Fill Form">
      <bpmn:extensionElements>
        <custom:property name="formId" value="42" />
        <custom:property name="formName" value="Main Form" />
      </bpmn:extensionElements>
    </bpmn:userTask>
    <bpmn:serviceTask id="Task_2" name="Auto Step">
      <bpmn:extensionElements>
        <custom:property name="formId" value="99" />
        <custom:property name="formReadOnly" value="true" />
      </bpmn:extensionElements>
    </bpmn:serviceTask>
    <bpmn:endEvent id="End_1" name="End" />
  </bpmn:process>
</bpmn:definitions>`

describe('bpmnFormBindings', () => {
  it('parses formId from userTask and serviceTask extension properties', () => {
    const map = parseBpmnNodeFormBindings(SAMPLE_BPMN)
    expect(map.size).toBe(2)

    const userTask = lookupNodeFormBinding(map, 'Task_1')
    expect(userTask?.formId).toBe(42)
    expect(userTask?.formName).toBe('Main Form')
    expect(userTask?.readOnly).toBe(false)

    const serviceTask = lookupNodeFormBinding(map, 'Task_2')
    expect(serviceTask?.formId).toBe(99)
    expect(serviceTask?.readOnly).toBe(true)
  })

  it('returns empty map for blank XML', () => {
    expect(parseBpmnNodeFormBindings('').size).toBe(0)
    expect(parseBpmnNodeFormBindings(null).size).toBe(0)
  })
})
