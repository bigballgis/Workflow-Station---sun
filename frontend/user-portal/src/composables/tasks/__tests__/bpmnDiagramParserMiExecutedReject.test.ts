import { beforeEach, describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { parseBpmnDiagram } from '../bpmnDiagramParser'
import { createApplicationDetailDiagramParser } from '../../applicationDetail/useApplicationDetailDiagramParser'
import type { ApplicationDetailCtx } from '../../applicationDetail/context'
import type { HistoryRecord } from '@/types/historyRecord'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

const xml = `<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
<process id="review_process"><startEvent id="start"/><subProcess id="rows" name="Row review">
<multiInstanceLoopCharacteristics/><startEvent id="row_start"/>
<userTask id="check" name="Check row"/><userTask id="finish" name="Finish row"/>
<endEvent id="row_end"/><sequenceFlow id="a" sourceRef="row_start" targetRef="check"/>
<sequenceFlow id="b" sourceRef="check" targetRef="finish"/>
<sequenceFlow id="c" sourceRef="finish" targetRef="row_end"/>
</subProcess><endEvent id="end"/><sequenceFlow id="d" sourceRef="start" targetRef="rows"/>
<sequenceFlow id="e" sourceRef="rows" targetRef="end"/></process></definitions>`

function record(nodeId: string, nodeName: string, status: HistoryRecord['status']): HistoryRecord {
  return { id: `${nodeId}_${status}`, nodeId, nodeName, status, createdTime: '' }
}

function parse(surface: string, current: string, history: HistoryRecord[]) {
  const taskName = current === 'check' ? 'Check row' : 'Finish row'
  if (surface === 'task') return parseBpmnDiagram(xml, {
    taskInfo: { taskDefinitionKey: current, taskName }, historyRecords: history,
    isCompletedTask: false, processState: 'RUNNING', t: key => key,
  })!
  const ctx = {
    t: (key: string) => key, snapshotTaskName: '', snapshotTaskDefinitionKey: '',
    processInfo: ref({ status: 'RUNNING', currentNode: taskName }),
    processNodes: ref([]), processFlows: ref([]), currentNodeId: ref(''),
    completedNodeIds: ref<string[]>([]), diagramReady: ref(false), historyRecords: ref(history),
    snapshotActivityId: ref(''), hasIncompleteMiRows: () => true, hasCompletedMiRows: () => false,
    diagramParseScheduled: false,
  } as unknown as ApplicationDetailCtx
  createApplicationDetailDiagramParser(ctx).parseBpmnXml(xml)
  return { processNodes: ctx.processNodes.value, completedNodeIds: ctx.completedNodeIds.value }
}

for (const surface of ['task', 'application']) describe(`${surface} running MI executed reject branch`, () => {
  beforeEach(clearBpmnParseCache)
  for (const pendingFirst of [true, false]) it(`shows an executed predecessor green with a pending sibling (pending first=${pendingFirst})`, () => {
    const records = [record('check', 'Check row', 'pending'), record('check', 'Check row', 'rejected')]
    if (!pendingFirst) records.reverse()
    const result = parse(surface, 'finish', records)
    expect(result.processNodes.find(n => n.id === 'check')?.status).toBe('completed')
    expect(result.completedNodeIds).toContain('check')
    expect(result.processNodes.find(n => n.id === 'finish')?.status).toBe('current')
  })
  it('does not turn a pending-only predecessor green', () => {
    const result = parse(surface, 'finish', [record('check', 'Check row', 'pending')])
    expect(result.processNodes.find(n => n.id === 'check')?.status).toBe('pending')
    expect(result.completedNodeIds).not.toContain('check')
  })
  it('does not turn a downstream task green from another row rejection', () => {
    const result = parse(surface, 'check', [record('finish', 'Finish row', 'rejected')])
    expect(result.processNodes.find(n => n.id === 'check')?.status).toBe('current')
    expect(result.processNodes.find(n => n.id === 'finish')?.status).toBe('pending')
    expect(result.completedNodeIds).not.toContain('finish')
  })
  it('keeps unvisited tasks pending', () => {
    const result = parse(surface, 'check', [])
    expect(result.processNodes.find(n => n.id === 'finish')?.status).toBe('pending')
  })
})
