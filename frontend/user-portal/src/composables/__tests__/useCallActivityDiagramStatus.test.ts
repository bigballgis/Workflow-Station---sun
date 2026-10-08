import { describe, expect, it } from 'vitest'
import { callActivityStatus, mergeCallActivityStatuses } from '../useCallActivityDiagramStatus'
import type { CalledFunctionUnitInstance } from '@/api/process'
import type { ProcessNode } from '@/components/ProcessDiagram.vue'

const XML = `<?xml version="1.0"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <bpmn:process id="p">
    <bpmn:userTask id="Raise" name="Raise Request"/>
    <bpmn:callActivity id="Call_Primary" name="Check Primary Vendor" calledElement="fu-vendor"/>
    <bpmn:callActivity id="Call_Extra" name="Check Additional Vendors" calledElement="fu-vendor"/>
  </bpmn:process>
</bpmn:definitions>`

const call = (callActivityId: string, status: string): CalledFunctionUnitInstance =>
  ({ processInstanceId: `${callActivityId}-${status}-${Math.random()}`, callActivityId, status })

const parsed: ProcessNode[] = [{ id: 'Raise', name: 'Raise Request', type: 'task', status: 'completed' }]

describe('callActivityStatus', () => {
  it('is current while any call it started is running, e.g. one row of a per-row call', () => {
    expect(callActivityStatus([call('c', 'COMPLETED'), call('c', 'RUNNING')])).toBe('current')
  })
  it('is rejected when a call was rejected and none still runs', () => {
    expect(callActivityStatus([call('c', 'COMPLETED'), call('c', 'REJECTED')])).toBe('rejected')
  })
  it('is completed once every call finished', () => {
    expect(callActivityStatus([call('c', 'COMPLETED')])).toBe('completed')
  })
  it('is unknown before the step is reached', () => {
    expect(callActivityStatus([])).toBeUndefined()
  })
})

describe('mergeCallActivityStatuses', () => {
  it('adds the call steps the parser left out, coloured by their calls', () => {
    const result = mergeCallActivityStatuses(XML, parsed, [call('Call_Primary', 'RUNNING')])

    expect(Array.isArray(result)).toBe(false)
    const { nodes, currentId } = result as { nodes: ProcessNode[]; currentId: string }
    expect(nodes.find(n => n.id === 'Call_Primary')?.status).toBe('current')
    expect(nodes.find(n => n.id === 'Call_Extra')?.status).toBe('pending')
    expect(nodes.find(n => n.id === 'Raise')?.status).toBe('completed')
    expect(currentId).toBe('Call_Primary')
  })

  it('returns the same list when applied twice, so the node watch settles', () => {
    const calls = [call('Call_Primary', 'COMPLETED')]
    const first = mergeCallActivityStatuses(XML, parsed, calls) as { nodes: ProcessNode[] }
    expect(mergeCallActivityStatuses(XML, first.nodes, calls)).toBe(first.nodes)
  })

  it('leaves diagrams without call steps untouched', () => {
    const plain = '<bpmn:definitions xmlns:bpmn="x"><bpmn:process id="p"/></bpmn:definitions>'
    expect(mergeCallActivityStatuses(plain, parsed, [])).toBe(parsed)
  })
})
