import type { BatchAssignmentHistoryRecord } from '@/api/process'

/** Historical task views must not acquire observations from later task actions. */
export function projectBatchAssignmentHistory(records: BatchAssignmentHistoryRecord[],
  snapshot: { time?: string; taskId?: string; name?: string }): BatchAssignmentHistoryRecord[] {
  if (!snapshot.time && !snapshot.taskId && !snapshot.name) return records
  const cutoff = snapshot.time ? Date.parse(snapshot.time) : Number.NaN
  return records.filter(record => {
    if (snapshot.taskId && record.taskId === snapshot.taskId) return true
    return Number.isFinite(cutoff) && Date.parse(record.operationTime) <= cutoff
  })
}
