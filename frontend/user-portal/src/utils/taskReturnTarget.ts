import type { ReturnableActivity } from '@/api/task'

/** Resolve BPMN activity id for form-create RETURN (rollback / draft). */
export function resolveRollbackTargetActivityId(
  targetStep: string,
  config: Record<string, unknown>,
  activities: ReturnableActivity[],
): { activityId: string; taskName?: string } | null {
  if (!activities.length) return null
  const step = (targetStep || 'previous').trim().toLowerCase()
  if (step === 'first' || step === 'initiator') {
    const last = activities[activities.length - 1]
    return last?.taskId ? { activityId: last.taskId, taskName: last.taskName } : null
  }
  if (step === 'specific') {
    const configured =
      (typeof config.targetActivityId === 'string' && config.targetActivityId.trim())
      || (typeof config.activityId === 'string' && config.activityId.trim())
      || ''
    if (configured) {
      const hit = activities.find((a) => a.taskId === configured)
      return { activityId: configured, taskName: hit?.taskName }
    }
  }
  const first = activities[0]
  return first?.taskId ? { activityId: first.taskId, taskName: first.taskName } : null
}
