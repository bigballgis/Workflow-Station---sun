import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailDiagramParser } from '../useApplicationDetailDiagramParser'
import type { ApplicationDetailCtx } from '../context'
import type { HistoryRecord } from '@/types/historyRecord'
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'

const xml = `<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="p">
<startEvent id="rootStart"/><subProcess id="lines" name="Parallel reviews">
<multiInstanceLoopCharacteristics/><startEvent id="lineStart"/>
<userTask id="review" name="Review"/><endEvent id="taken" name="Finished"/>
<endEvent id="skipped" name="Bypassed"/></subProcess><endEvent id="rootEnd" name="Finished"/>
</process></definitions>`
const record = (nodeId: string, nodeName: string, status: HistoryRecord['status'] = 'completed'): HistoryRecord => ({
  id: nodeId, nodeId, nodeName, status, createdTime: '',
})
function makeCtx(
  historyRecords: HistoryRecord[],
  status: string,
  currentNode = '',
) {
  return {
    t: (key: string) => key,
    snapshotTaskName: '',
    snapshotTaskDefinitionKey: '',
    processInfo: ref({ status, currentNode }),
    processNodes: ref([]),
    processFlows: ref([]),
    currentNodeId: ref(''),
    completedNodeIds: ref<string[]>([]),
    diagramReady: ref(false),
    historyRecords: ref(historyRecords),
    snapshotActivityId: ref(''),
    hasIncompleteMiRows: () => false,
    hasCompletedMiRows: () => false,
    diagramParseScheduled: false,
  } as unknown as ApplicationDetailCtx
}


describe('application end events use their own execution history', () => {
  it.each(['COMPLETED', 'REJECTED'])('keeps skipped MI exits pending after %s', status => {
    clearBpmnParseCache()
    const ctx = makeCtx([record('rootStart', 'Start'), record('lineStart', 'Start'),
      record('review', 'Review'), record('taken', 'Finished'), record('rootEnd', 'Finished')], status)
    createApplicationDetailDiagramParser(ctx).parseBpmnXml(xml)
    const nodes = new Map(ctx.processNodes.value.map(n => [n.id, n.status]))
    expect(nodes.get('taken')).toBe('completed')
    expect(nodes.get('rootEnd')).toBe('completed')
    expect(nodes.get('skipped')).toBe('pending')
    expect(ctx.completedNodeIds.value).not.toContain('skipped')
  })
  it('uses recorded rejection without relying on the event label', () => {
    clearBpmnParseCache()
    const ctx = makeCtx([record('rootStart', 'Start'), record('lineStart', 'Start'),
      record('review', 'Review'), record('taken', 'Finished', 'rejected')], 'REJECTED')
    createApplicationDetailDiagramParser(ctx).parseBpmnXml(xml)
    expect(ctx.processNodes.value.find(n => n.id === 'taken')?.status).toBe('rejected')
    expect(ctx.processNodes.value.find(n => n.id === 'skipped')?.status).toBe('pending')
  })
})


it('shows an ended MI subprocess completed when an optional task was skipped on rejection', () => {
  clearBpmnParseCache()
  const ctx = makeCtx([record('rootStart', 'Start'), record('lineStart', 'Start'),
    record('review', 'Review'), record('taken', 'Finished')], 'REJECTED')
  const branchXml = xml.replace('<endEvent id="taken"', '<userTask id="optional" name="Optional review"/><endEvent id="taken"')
  createApplicationDetailDiagramParser(ctx).parseBpmnXml(branchXml)
  expect(ctx.processNodes.value.find(n => n.id === 'lines')?.status).toBe('completed')
  expect(ctx.processNodes.value.find(n => n.id === 'optional')?.status).toBe('pending')
})


it('does not mark an unentered MI container completed just because the process was rejected', () => {
  clearBpmnParseCache()
  const ctx = makeCtx([record('rootStart', 'Start')], 'REJECTED')
  createApplicationDetailDiagramParser(ctx).parseBpmnXml(xml)
  expect(ctx.processNodes.value.find(n => n.id === 'lines')?.status).toBe('pending')
})
