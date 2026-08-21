import { describe, it, expect } from 'vitest'
import { parseBpmnNodeFormBindings, lookupNodeFormBinding, isTaskElement, TASK_LOCAL_NAMES } from '@/utils/bpmnFormBindings'

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
    <bpmn:task id="Task_3" name="Generic Task">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="50021" />
          <custom:property name="formName" value="test578" />
        </custom:properties>
      </bpmn:extensionElements>
    </bpmn:task>
    <bpmn:scriptTask id="Task_4" name="Script Step">
      <bpmn:extensionElements>
        <custom:property name="formId" value="77" />
      </bpmn:extensionElements>
    </bpmn:scriptTask>
    <bpmn:endEvent id="End_1" name="End" />
  </bpmn:process>
</bpmn:definitions>`

describe('bpmnFormBindings', () => {
  it('parses formId from all task types including generic task', () => {
    const map = parseBpmnNodeFormBindings(SAMPLE_BPMN)
    expect(map.size).toBe(4)

    const userTask = lookupNodeFormBinding(map, 'Task_1')
    expect(userTask?.formId).toBe(42)
    expect(userTask?.formName).toBe('Main Form')
    expect(userTask?.readOnly).toBe(false)
    expect(userTask?.nodeType).toBe('userTask')

    const serviceTask = lookupNodeFormBinding(map, 'Task_2')
    expect(serviceTask?.formId).toBe(99)
    expect(serviceTask?.readOnly).toBe(true)
    expect(serviceTask?.nodeType).toBe('serviceTask')

    const genericTask = lookupNodeFormBinding(map, 'Task_3')
    expect(genericTask?.formId).toBe(50021)
    expect(genericTask?.formName).toBe('test578')
    expect(genericTask?.nodeType).toBe('task')

    const scriptTask = lookupNodeFormBinding(map, 'Task_4')
    expect(scriptTask?.formId).toBe(77)
    expect(scriptTask?.nodeType).toBe('scriptTask')
  })

  it('returns empty map for blank XML', () => {
    expect(parseBpmnNodeFormBindings('').size).toBe(0)
    expect(parseBpmnNodeFormBindings(null).size).toBe(0)
  })
})

/**
 * Most deployed function units bind forms through these extension properties
 * rather than the stage-binding table, so this is the path the scene split has
 * to survive.
 */
describe('bpmnFormBindings — portal scene', () => {
  const BOTH_SCENES_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:custom="http://workflow.platform/schema/custom">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:userTask id="Task_1" name="Approve">
      <bpmn:extensionElements>
        <custom:property name="formId" value="10" />
        <custom:property name="formName" value="Approve (edit)" />
        <custom:property name="requestFormId" value="11" />
        <custom:property name="requestFormName" value="Approve (read-only)" />
      </bpmn:extensionElements>
    </bpmn:userTask>
    <bpmn:userTask id="Task_2" name="Review">
      <bpmn:extensionElements>
        <custom:property name="formId" value="20" />
        <custom:property name="formName" value="Review (edit)" />
      </bpmn:extensionElements>
    </bpmn:userTask>
  </bpmn:process>
</bpmn:definitions>`

  it('resolves each scene to its own design on the same node', () => {
    const map = parseBpmnNodeFormBindings(BOTH_SCENES_BPMN)

    expect(lookupNodeFormBinding(map, 'Task_1', 'TASK')?.formId).toBe(10)
    expect(lookupNodeFormBinding(map, 'Task_1', 'REQUEST')?.formId).toBe(11)
  })

  it('marks the My Requests design read-only without needing a flag', () => {
    const map = parseBpmnNodeFormBindings(BOTH_SCENES_BPMN)

    expect(lookupNodeFormBinding(map, 'Task_1', 'REQUEST')?.readOnly).toBe(true)
  })

  /** Falling back would silently render the editable To Do layout in My Requests. */
  it('returns nothing for a scene the node does not bind, instead of falling back', () => {
    const map = parseBpmnNodeFormBindings(BOTH_SCENES_BPMN)

    expect(lookupNodeFormBinding(map, 'Task_2', 'TASK')?.formId).toBe(20)
    expect(lookupNodeFormBinding(map, 'Task_2', 'REQUEST')).toBeNull()
  })

  it('reads diagrams without any scene property as the To Do design', () => {
    const map = parseBpmnNodeFormBindings(SAMPLE_BPMN)

    expect(lookupNodeFormBinding(map, 'Task_1', 'TASK')?.formId).toBe(42)
    expect(lookupNodeFormBinding(map, 'Task_1')?.scene).toBe('TASK')
    expect(lookupNodeFormBinding(map, 'Task_1', 'REQUEST')).toBeNull()
  })
})

describe('isTaskElement', () => {
  it('recognizes all BPMN 2.0 task types', () => {
    for (const name of TASK_LOCAL_NAMES) {
      expect(isTaskElement(name)).toBe(true)
    }
  })

  it('rejects non-task element types', () => {
    expect(isTaskElement('startEvent')).toBe(false)
    expect(isTaskElement('endEvent')).toBe(false)
    expect(isTaskElement('sequenceFlow')).toBe(false)
    expect(isTaskElement('process')).toBe(false)
    expect(isTaskElement('')).toBe(false)
    expect(isTaskElement(undefined)).toBe(false)
  })
})
