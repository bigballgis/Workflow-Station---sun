import { describe, expect, it } from 'vitest'
import { recordNoteNotificationNodes } from '../recordNoteNotificationNodes'

const read = (xml: string) => recordNoteNotificationNodes(new DOMParser().parseFromString(xml, 'application/xml'))

describe('Record Note targets follow explicit assignment configuration', () => {
  it('accepts nested configured MI tasks and preserves stable IDs after renaming', () => {
    const xml = `<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:f="http://flowable.org/bpmn">
      <process><subProcess id="mi"><extensionElements><f:properties>
        <f:property name="subTableName" value="arbitrary_lines"/><f:property name="assigneeField" value="person_x9"/>
      </f:properties></extensionElements><multiInstanceLoopCharacteristics/>
      <subProcess id="nested"><userTask id="stable-node" name="Renamed handler"/></subProcess>
      </subProcess><userTask id="unrelated" name="Sub-Task"/></process></definitions>`
    expect(read(xml)).toEqual([{ id: 'stable-node', name: 'Renamed handler' }])
  })
  it('rejects a familiar label or table name without an MI assignment configuration', () => {
    expect(read(`<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
      <process><userTask id="fake" name="Credit Card Transaction"/></process></definitions>`)).toEqual([])
  })
  it('supports a standalone MI user task with a team field', () => {
    expect(read(`<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:f="http://flowable.org/bpmn">
      <process><userTask id="team"><extensionElements><f:properties>
      <f:property name="subTableName" value="xyz"/><f:property name="roleField" value="arbitrary_team"/>
      </f:properties></extensionElements><multiInstanceLoopCharacteristics/></userTask></process></definitions>`))
      .toEqual([{ id: 'team', name: 'team' }])
  })
})
