import type { ProcessNode } from '@/components/ProcessDiagram.vue'

export type SendTaskHistoryRecord = {
  nodeId?: string
  nodeName?: string
  status?: string
  action?: string
}

export const ckSendTaskId = (s: unknown) => String(s ?? '').trim()
export const normSendTaskLabel = (s: unknown) => ckSendTaskId(s).replace(/\s+/g, ' ')

function isSendHistoryCompleted(record: SendTaskHistoryRecord): boolean {
  if (record.status === 'rejected') return true
  if (record.status === 'completed') return true
  return record.action === 'send'
}

function historyMatchesSendTask(record: SendTaskHistoryRecord, id: string, name: string): boolean {
  if (ckSendTaskId(record.nodeId) === ckSendTaskId(id)) return true
  const recordName = normSendTaskLabel(record.nodeName)
  const nodeName = normSendTaskLabel(name)
  return recordName.length > 0 && nodeName.length > 0 && recordName === nodeName
}

export function buildSendTaskCompletedLookups(historyRecords: SendTaskHistoryRecord[]): {
  completedIds: Set<string>
  completedNames: Set<string>
} {
  const completedIds = new Set<string>()
  const completedNames = new Set<string>()
  for (const record of historyRecords) {
    if (!isSendHistoryCompleted(record)) continue
    if (record.nodeId) completedIds.add(ckSendTaskId(record.nodeId))
    if (record.nodeName) completedNames.add(normSendTaskLabel(record.nodeName))
  }
  return { completedIds, completedNames }
}

export function resolveSendTaskDiagramStatus(
  historyRecords: SendTaskHistoryRecord[],
  id: string,
  name: string,
  lookups?: { completedIds: Set<string>; completedNames: Set<string> },
): Extract<ProcessNode['status'], 'completed' | 'rejected' | 'pending'> {
  const { completedIds, completedNames } = lookups ?? buildSendTaskCompletedLookups(historyRecords)
  if (completedIds.has(ckSendTaskId(id)) || completedNames.has(normSendTaskLabel(name))) {
    return 'completed'
  }
  const match = historyRecords.find(record => historyMatchesSendTask(record, id, name))
  if (match?.status === 'rejected') return 'rejected'
  if (match && isSendHistoryCompleted(match)) return 'completed'
  return 'pending'
}
