import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailBpmnCurrentForm } from '../useApplicationDetailBpmnCurrentForm'
import type { ApplicationDetailCtx } from '../context'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

function makeCtx(currentNode: string) {
  const ctx = {
    snapshotTaskName: '',
    snapshotTaskDefinitionKey: '',
    snapshotActivityId: ref(''),
    processInfo: ref({ status: 'RUNNING', currentNode }),
    bpmnXml: ref(''),
  }
  return ctx as unknown as ApplicationDetailCtx
}

const xmlWithBothScenes = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://test">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="Start_1" name="Start" />
    <bpmn:userTask id="UserTask_participants" name="Participants">
      <bpmn:extensionElements>
        <camunda:properties xmlns:camunda="http://camunda.org/schema/1.0/bpmn">
          <camunda:property name="formId" value="task-form-1" />
          <camunda:property name="formName" value="Participants" />
          <camunda:property name="requestFormId" value="request-form-1" />
          <camunda:property name="requestFormName" value="Participants (My Request)" />
        </camunda:properties>
      </bpmn:extensionElements>
    </bpmn:userTask>
    <bpmn:endEvent id="End_1" name="End" />
    <bpmn:sequenceFlow id="f1" sourceRef="Start_1" targetRef="UserTask_participants" />
    <bpmn:sequenceFlow id="f2" sourceRef="UserTask_participants" targetRef="End_1" />
  </bpmn:process>
</bpmn:definitions>`

const xmlWithTaskSceneOnly = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://test">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="Start_1" name="Start" />
    <bpmn:userTask id="UserTask_approve" name="Approve">
      <bpmn:extensionElements>
        <camunda:properties xmlns:camunda="http://camunda.org/schema/1.0/bpmn">
          <camunda:property name="formId" value="task-form-2" />
          <camunda:property name="formName" value="Approve" />
        </camunda:properties>
      </bpmn:extensionElements>
    </bpmn:userTask>
    <bpmn:endEvent id="End_1" name="End" />
    <bpmn:sequenceFlow id="f1" sourceRef="Start_1" targetRef="UserTask_approve" />
    <bpmn:sequenceFlow id="f2" sourceRef="UserTask_approve" targetRef="End_1" />
  </bpmn:process>
</bpmn:definitions>`

describe('useApplicationDetailBpmnCurrentForm — My Requests/Audit prefer REQUEST scene', () => {
  it('picks requestFormId/requestFormName when the node has a REQUEST design', () => {
    clearBpmnParseCache()
    const ctx = makeCtx('Participants')
    const { parseBpmnXmlAndGetFormId } = createApplicationDetailBpmnCurrentForm(ctx)

    const result = parseBpmnXmlAndGetFormId(xmlWithBothScenes)

    expect(result).toEqual({
      formId: 'request-form-1',
      formName: 'Participants (My Request)',
      scene: 'REQUEST',
    })
  })

  it('falls back to the TASK design when no REQUEST design exists on the node', () => {
    clearBpmnParseCache()
    const ctx = makeCtx('Approve')
    const { parseBpmnXmlAndGetFormId } = createApplicationDetailBpmnCurrentForm(ctx)

    const result = parseBpmnXmlAndGetFormId(xmlWithTaskSceneOnly)

    expect(result).toEqual({
      formId: 'task-form-2',
      formName: 'Approve',
      scene: 'TASK',
    })
  })
})
