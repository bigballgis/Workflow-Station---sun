import { ElMessage, ElMessageBox } from 'element-plus'
import type { BatchCompleteResult, TaskInfo } from '@/api/task'

export async function reportBatchCompletion(result: BatchCompleteResult, tasks: TaskInfo[], title: string,
  t: (key: string, values?: Record<string, string | number>) => string): Promise<void> {
  const { succeeded, failed } = result
  const pending = result.pending ?? []
  if (pending.length > 0) {
    ElMessage.warning({ message: t('task.assignmentCompletion.batchPending',
      { ok: succeeded.length, failed: failed.length, pending: pending.length }), duration: 0, showClose: true })
  } else if (failed.length === 0) {
    ElMessage.success(t('task.batchAllSucceeded', { count: succeeded.length }))
  }
  if (failed.length > 0) {
    const label = (task: string) => tasks.find(row => row.taskId === task)?.requestId || task
    const lines = failed.map(row => `${label(row.taskId)}: ${row.reason}`).join('\n')
    await ElMessageBox.alert(`${t('task.batchPartialResult', { ok: succeeded.length, failed: failed.length })}\n${lines}`,
      title, { type: succeeded.length > 0 || pending.length > 0 ? 'warning' : 'error', customStyle: { whiteSpace: 'pre-line' } })
      .catch(() => undefined)
  }
}
