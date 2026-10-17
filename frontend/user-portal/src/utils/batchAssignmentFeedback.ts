import type { BatchAssignmentVerification } from '@/api/process'

/** An audit-write error supplements, never hides, a known assignment failure. */
export function batchAssignmentFeedback(result: BatchAssignmentVerification, t: (key: string) => string,
  context: 'task' | 'processStart'): string {
  const parts: string[] = []
  if (result.status !== 'VERIFIED') parts.push(t(`${context}.${result.status === 'FAILED' ? 'batchAssignmentFailed' : 'batchAssignmentUnconfirmed'}`))
  if (result.historyRecorded === false) parts.push(t('task.batchHistory.saveFailed'))
  return parts.join(' ')
}
