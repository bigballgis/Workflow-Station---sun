import { describe, expect, it } from 'vitest'
import { queryHumanWorkflowTasks, isHumanWorkflowTask } from '@/utils/bpmnHumanWorkflowTasks'

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <bpmn:process id="Process_1">
    <bpmn:task id="Activity_0bydtjc" name="test" />
  </bpmn:process>
</bpmn:definitions>`

describe('bpmnHumanWorkflowTasks', () => {
  it('finds generic bpmn:task elements', () => {
    const doc = new DOMParser().parseFromString(xml, 'text/xml')
    const tasks = queryHumanWorkflowTasks(doc)
    expect(tasks.length).toBe(1)
    expect(tasks[0].getAttribute('id')).toBe('Activity_0bydtjc')
    expect(isHumanWorkflowTask(tasks[0].localName)).toBe(true)
  })
})
