import type { ProcessNode } from '@/components/ProcessDiagram.vue'

const ck = (s: unknown) => String(s ?? '').trim()
const norm = (s: unknown) => ck(s).replace(/\s+/g, ' ')

export type EndEventHistoryRecord = {
  nodeId?: string
  nodeName?: string
  status?: string
}

export function resolveEndEventDiagramStatus(
  historyRecords: EndEventHistoryRecord[],
  id: string,
  name: string,
): Extract<ProcessNode['status'], 'completed' | 'rejected' | 'pending'> {
  const match = historyRecords.find(record => {
    if (ck(record.nodeId) === ck(id)) return true
    const recordName = norm(record.nodeName)
    const nodeName = norm(name)
    return recordName.length > 0 && nodeName.length > 0 && recordName === nodeName
  })
  if (!match) return 'pending'
  if (match.status === 'rejected') return 'rejected'
  if (match.status === 'completed') return 'completed'
  return 'pending'
}
