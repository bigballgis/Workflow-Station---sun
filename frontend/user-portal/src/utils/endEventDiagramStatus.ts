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
  // An activity ID is authoritative; equal labels on different exits are not identities.
  const match = historyRecords.find(record => ck(record.nodeId) === ck(id))
    ?? historyRecords.find(record => !ck(record.nodeId) && norm(name).length > 0
      && norm(record.nodeName) === norm(name))
  if (!match) return 'pending'
  if (match.status === 'rejected') return 'rejected'
  if (match.status === 'completed') return 'completed'
  return 'pending'
}
