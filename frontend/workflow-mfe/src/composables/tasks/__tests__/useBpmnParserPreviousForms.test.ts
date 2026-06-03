/**
 * Coverage for the four field-carry-forward scenarios over multi-instance subProcesses, exercising
 * `parseBpmnXmlAndGetPreviousFormIds`. The BFS feeds the readonly "previous form" snapshot panel in
 * `tasks/detail.vue`, which is the only Path-2 channel by which an upstream user task's submitted
 * values can surface on a downstream task's page (alongside Path 1 same-key transfer via process
 * variables, which is enforced server-side by `TaskFormComponent.submitTaskForm` writing to
 * `ProcessInstance.variables`).
 *
 * Scenarios:
 *   1) main → main           — A ▸ B on the same main flow
 *   2) main → MI sub-task    — A ▸ MI subProcess [ B ] (B inside subProc)
 *   3) MI sub-task → main    — A ▸ MI subProcess [ B ] ▸ C on main flow (current = C)
 *   4) intra-MI              — MI subProcess [ B1 ▸ B2 ] (current = B2; B1 is sibling)
 */

import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

import { useBpmnParser } from '../useBpmnParser'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

function setupParser(taskDefinitionKey: string) {
  clearBpmnParseCache()
  const taskInfo = ref<Record<string, any>>({ taskDefinitionKey, taskName: '' })
  const historyRecords = ref<any[]>([])
  const isCompletedTask = ref<boolean>(false)
  return useBpmnParser({ taskInfo, historyRecords, isCompletedTask })
}

/** Wrap inline BPMN bodies with the standard envelope so jsdom DOMParser keeps prefixed attrs. */
function bpmn(processBody: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn"
                  targetNamespace="http://workflow-station/test">
  <bpmn:process id="Process_1" isExecutable="true">
    ${processBody}
  </bpmn:process>
</bpmn:definitions>`
}

const userTask = (id: string, name: string, formId: string) => `
    <bpmn:userTask id="${id}" name="${name}">
      <bpmn:extensionElements>
        <flowable:property name="formId" value="${formId}" />
        <flowable:property name="formName" value="${name}-form" />
      </bpmn:extensionElements>
    </bpmn:userTask>`

const flow = (id: string, source: string, target: string) =>
  `<bpmn:sequenceFlow id="${id}" sourceRef="${source}" targetRef="${target}" />`

describe('parseBpmnXmlAndGetPreviousFormIds — MI field carry-forward scenarios', () => {
  it('Scenario 1: main → main — sequential userTasks on main flow', () => {
    const xml = bpmn(`
      <bpmn:startEvent id="Start_1" />
      ${userTask('UserTask_A', 'Task A', '11')}
      ${userTask('UserTask_B', 'Task B', '12')}
      <bpmn:endEvent id="End_1" />
      ${flow('flow_sa', 'Start_1', 'UserTask_A')}
      ${flow('flow_ab', 'UserTask_A', 'UserTask_B')}
      ${flow('flow_be', 'UserTask_B', 'End_1')}
    `)
    const { parseBpmnXmlAndGetPreviousFormIds } = setupParser('UserTask_B')
    const result = parseBpmnXmlAndGetPreviousFormIds(xml)
    expect(result.map(r => r.formId)).toEqual(['11'])
  })

  it('Scenario 2: main → MI sub-task — current task lives inside MI subProcess', () => {
    const xml = bpmn(`
      <bpmn:startEvent id="Start_1" />
      ${userTask('UserTask_A', 'Task A', '11')}
      <bpmn:subProcess id="MI_Sub_1" name="MI subprocess">
        <bpmn:multiInstanceLoopCharacteristics flowable:collection="participants" flowable:elementVariable="currentItem" />
        <bpmn:startEvent id="MI_Start_1" />
        ${userTask('UserTask_B', 'Sub form 1', '12')}
        <bpmn:endEvent id="MI_End_1" />
        ${flow('mflow_sb', 'MI_Start_1', 'UserTask_B')}
        ${flow('mflow_be', 'UserTask_B', 'MI_End_1')}
      </bpmn:subProcess>
      <bpmn:endEvent id="End_1" />
      ${flow('flow_sa', 'Start_1', 'UserTask_A')}
      ${flow('flow_am', 'UserTask_A', 'MI_Sub_1')}
      ${flow('flow_me', 'MI_Sub_1', 'End_1')}
    `)
    const { parseBpmnXmlAndGetPreviousFormIds } = setupParser('UserTask_B')
    const result = parseBpmnXmlAndGetPreviousFormIds(xml)
    expect(result.map(r => r.formId)).toEqual(['11'])
  })

  it('Scenario 2 regression: MI startEvent first in XML must not be picked as main start', () => {
    /** Repro for the original #1392 root cause — MI inner startEvent precedes main startEvent. */
    const xml = bpmn(`
      <bpmn:subProcess id="MI_Sub_1" name="MI subprocess">
        <bpmn:multiInstanceLoopCharacteristics flowable:collection="participants" flowable:elementVariable="currentItem" />
        <bpmn:startEvent id="MI_Start_1" />
        ${userTask('UserTask_B', 'Sub form 1', '12')}
        <bpmn:endEvent id="MI_End_1" />
        ${flow('mflow_sb', 'MI_Start_1', 'UserTask_B')}
        ${flow('mflow_be', 'UserTask_B', 'MI_End_1')}
      </bpmn:subProcess>
      <bpmn:startEvent id="Start_1" />
      ${userTask('UserTask_A', 'Task A', '11')}
      <bpmn:endEvent id="End_1" />
      ${flow('flow_sa', 'Start_1', 'UserTask_A')}
      ${flow('flow_am', 'UserTask_A', 'MI_Sub_1')}
      ${flow('flow_me', 'MI_Sub_1', 'End_1')}
    `)
    const { parseBpmnXmlAndGetPreviousFormIds } = setupParser('UserTask_B')
    const result = parseBpmnXmlAndGetPreviousFormIds(xml)
    expect(result.map(r => r.formId)).toEqual(['11'])
  })

  it('Scenario 3: MI sub-task → main — downstream main task sees MI inner forms as previous', () => {
    const xml = bpmn(`
      <bpmn:startEvent id="Start_1" />
      ${userTask('UserTask_A', 'Task A', '11')}
      <bpmn:subProcess id="MI_Sub_1" name="MI subprocess">
        <bpmn:multiInstanceLoopCharacteristics flowable:collection="participants" flowable:elementVariable="currentItem" />
        <bpmn:startEvent id="MI_Start_1" />
        ${userTask('UserTask_B', 'Sub form 1', '12')}
        <bpmn:endEvent id="MI_End_1" />
        ${flow('mflow_sb', 'MI_Start_1', 'UserTask_B')}
        ${flow('mflow_be', 'UserTask_B', 'MI_End_1')}
      </bpmn:subProcess>
      ${userTask('UserTask_C', 'Task C', '13')}
      <bpmn:endEvent id="End_1" />
      ${flow('flow_sa', 'Start_1', 'UserTask_A')}
      ${flow('flow_am', 'UserTask_A', 'MI_Sub_1')}
      ${flow('flow_mc', 'MI_Sub_1', 'UserTask_C')}
      ${flow('flow_ce', 'UserTask_C', 'End_1')}
    `)
    const { parseBpmnXmlAndGetPreviousFormIds } = setupParser('UserTask_C')
    const result = parseBpmnXmlAndGetPreviousFormIds(xml)
    /**
     * UserTask_A (main) AND UserTask_B (MI inner) must both surface — without Fix A, only the main-flow
     * UserTask_A was reachable via BFS while UserTask_B's submitted snapshot stayed invisible to the
     * downstream main user task even though Path 1 (same-key) had already carried its values forward.
     */
    expect(result.map(r => r.formId).sort()).toEqual(['11', '12'])
  })

  it('Scenario 4: intra-MI — second inner userTask sees first inner userTask as previous', () => {
    /**
     * Sequential MI with two inner user tasks: B1 ▸ B2. When the participant opens B2, B1's submitted
     * values must appear in the previous-form panel; without Fix B the outer BFS short-circuits at
     * `stopSubProcessId` and never reaches B1 because there is no main-flow edge into MI inner tasks.
     */
    const xml = bpmn(`
      <bpmn:startEvent id="Start_1" />
      ${userTask('UserTask_A', 'Task A', '11')}
      <bpmn:subProcess id="MI_Sub_1" name="MI subprocess">
        <bpmn:multiInstanceLoopCharacteristics flowable:collection="participants" flowable:elementVariable="currentItem" />
        <bpmn:startEvent id="MI_Start_1" />
        ${userTask('UserTask_B1', 'Sub form 1', '12')}
        ${userTask('UserTask_B2', 'Sub form 2', '13')}
        <bpmn:endEvent id="MI_End_1" />
        ${flow('mflow_sb1', 'MI_Start_1', 'UserTask_B1')}
        ${flow('mflow_b1b2', 'UserTask_B1', 'UserTask_B2')}
        ${flow('mflow_b2e', 'UserTask_B2', 'MI_End_1')}
      </bpmn:subProcess>
      <bpmn:endEvent id="End_1" />
      ${flow('flow_sa', 'Start_1', 'UserTask_A')}
      ${flow('flow_am', 'UserTask_A', 'MI_Sub_1')}
      ${flow('flow_me', 'MI_Sub_1', 'End_1')}
    `)
    const { parseBpmnXmlAndGetPreviousFormIds } = setupParser('UserTask_B2')
    const result = parseBpmnXmlAndGetPreviousFormIds(xml)
    /** Main-flow A (11) plus same-MI sibling B1 (12) — both must precede B2. */
    expect(result.map(r => r.formId).sort()).toEqual(['11', '12'])
  })

  it('Boundary: sibling main-flow userTask AFTER an MI subProcess must NOT leak as previous', () => {
    /** Verifies the existing #1392 protection still holds after the new fixes. */
    const xml = bpmn(`
      <bpmn:startEvent id="Start_1" />
      ${userTask('UserTask_A', 'Task A', '11')}
      <bpmn:subProcess id="MI_Sub_1" name="MI subprocess">
        <bpmn:multiInstanceLoopCharacteristics flowable:collection="participants" flowable:elementVariable="currentItem" />
        <bpmn:startEvent id="MI_Start_1" />
        ${userTask('UserTask_B', 'Sub form 1', '12')}
        <bpmn:endEvent id="MI_End_1" />
        ${flow('mflow_sb', 'MI_Start_1', 'UserTask_B')}
        ${flow('mflow_be', 'UserTask_B', 'MI_End_1')}
      </bpmn:subProcess>
      ${userTask('UserTask_D', 'Task D (downstream)', '14')}
      <bpmn:endEvent id="End_1" />
      ${flow('flow_sa', 'Start_1', 'UserTask_A')}
      ${flow('flow_am', 'UserTask_A', 'MI_Sub_1')}
      ${flow('flow_md', 'MI_Sub_1', 'UserTask_D')}
      ${flow('flow_de', 'UserTask_D', 'End_1')}
    `)
    /** UserTask_B (current) must NOT see UserTask_D in previousForms even though they share main flow. */
    const { parseBpmnXmlAndGetPreviousFormIds } = setupParser('UserTask_B')
    const result = parseBpmnXmlAndGetPreviousFormIds(xml)
    expect(result.map(r => r.formId)).toEqual(['11'])
    expect(result.map(r => r.formId)).not.toContain('14')
  })

  it('De-dup: repeated formId across siblings is collapsed to one entry', () => {
    const xml = bpmn(`
      <bpmn:startEvent id="Start_1" />
      ${userTask('UserTask_A1', 'Task A', '11')}
      ${userTask('UserTask_A2', 'Task A copy', '11')}
      ${userTask('UserTask_B', 'Task B', '12')}
      <bpmn:endEvent id="End_1" />
      ${flow('flow_sa1', 'Start_1', 'UserTask_A1')}
      ${flow('flow_a1a2', 'UserTask_A1', 'UserTask_A2')}
      ${flow('flow_a2b', 'UserTask_A2', 'UserTask_B')}
      ${flow('flow_be', 'UserTask_B', 'End_1')}
    `)
    const { parseBpmnXmlAndGetPreviousFormIds } = setupParser('UserTask_B')
    const result = parseBpmnXmlAndGetPreviousFormIds(xml)
    expect(result.map(r => r.formId)).toEqual(['11'])
  })

  it('Empty result when current task is unreachable / not present', () => {
    const xml = bpmn(`
      <bpmn:startEvent id="Start_1" />
      ${userTask('UserTask_A', 'Task A', '11')}
      <bpmn:endEvent id="End_1" />
      ${flow('flow_sa', 'Start_1', 'UserTask_A')}
      ${flow('flow_ae', 'UserTask_A', 'End_1')}
    `)
    const { parseBpmnXmlAndGetPreviousFormIds } = setupParser('UserTask_Missing')
    expect(parseBpmnXmlAndGetPreviousFormIds(xml)).toEqual([])
  })
})
