import type { BatchAssignmentVerification } from '@/api/process'

export interface BatchCompleteResult {
  succeeded: string[]
  failed: { taskId: string; reason: string }[]
  pending?: { taskId: string; operationId: string; state: 'PENDING_SYNC' | 'SYNC_CONFLICT' | 'DELIVERY_UNKNOWN' }[]
  completedOperationIds?: Record<string, string>
}

export interface TaskCompletionResult {
  batchAssignmentVerification?: BatchAssignmentVerification | null
  operationId?: string | null
  state?: 'READY' | 'PENDING_SYNC' | 'SYNC_CONFLICT' | null
}
