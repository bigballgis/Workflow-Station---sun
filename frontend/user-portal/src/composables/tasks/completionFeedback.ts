import { ElMessage } from 'element-plus'
import type { TaskCompletionResult } from '@/api/task'
import { batchAssignmentFeedback } from '@/utils/batchAssignmentFeedback'
import { CompletionDeliveryUnknown, CompletionRejected } from '@/utils/completionOperation'
import { pickHttpErrorBodyMessage } from '@/utils/httpErrorMessage'

export function showCompletionFailure(error: unknown, t: (key: string) => string, unknown: (operationId: string) => void): boolean {
  if (error instanceof CompletionDeliveryUnknown) {
    unknown(error.operationId)
    ElMessage({ type: 'warning', message: t('task.assignmentCompletion.unknown'), duration: 0, showClose: true })
    return true
  }
  if (error instanceof CompletionRejected) {
    ElMessage.error(pickHttpErrorBodyMessage(error.response.data) || t('task.operationFailed'))
    return true
  }
  return false
}

/** False keeps the submitted form visible while its accepted effect awaits synchronization. */
export function showCompletionFeedback(result: TaskCompletionResult | null | undefined, t: (key: string) => string): boolean {
  if (result?.state === 'PENDING_SYNC' || result?.state === 'SYNC_CONFLICT') {
    ElMessage({ type: 'warning', message: t(`task.assignmentCompletion.${result.state === 'PENDING_SYNC' ? 'pending' : 'conflict'}`),
      duration: 0, showClose: true })
    return false
  }
  const verification = result?.batchAssignmentVerification
  if (verification && (verification.status !== 'VERIFIED' || verification.historyRecorded === false)) {
    ElMessage({ type: 'warning', message: batchAssignmentFeedback(verification, t, 'task'), duration: 0, showClose: true })
  } else ElMessage.success(t('task.operationSuccess'))
  return true
}
