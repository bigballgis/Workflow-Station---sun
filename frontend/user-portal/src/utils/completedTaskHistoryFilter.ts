/**
 * Completed-task snapshot must not hide automatic nodes that already ran after
 * the human task ended (email sendTask → serviceTask, gateways, end events).
 * Later human tasks stay outside the snapshot even if the process has already ended.
 */

export type CompletedHistoryFilterItem = {
  taskId?: string | null
  operationTime?: string | null
  operationType?: string | null
  activityType?: string | null
}

export type CompletedHistoryFilterOptions = {
  isCompletedTask: boolean
  hasSnapshotRoute: boolean
  snapshotTaskId?: string
  snapshotTime?: string
}

export function isPostSnapshotAutomaticActivity(item: CompletedHistoryFilterItem): boolean {
  const op = String(item.operationType || '').toUpperCase()
  if (op === 'SEND' || op === 'GATEWAY') return true
  const type = localActivityType(item.activityType)
  return type === 'serviceTask'
    || type === 'sendTask'
    || type === 'endEvent'
    || type === 'exclusiveGateway'
    || type === 'parallelGateway'
    || type === 'inclusiveGateway'
}

export function shouldKeepCompletedHistoryItem(
  item: CompletedHistoryFilterItem,
  opts: CompletedHistoryFilterOptions,
): boolean {
  if (!opts.isCompletedTask || !opts.hasSnapshotRoute) return true
  if (opts.snapshotTaskId && item.taskId === opts.snapshotTaskId) return true
  if (isPostSnapshotAutomaticActivity(item)) return true
  return isWithinCompletedSnapshot(item.operationTime, opts.snapshotTime)
}

function localActivityType(activityType?: string | null): string {
  const raw = String(activityType || '').trim()
  const colon = raw.lastIndexOf(':')
  return colon >= 0 ? raw.slice(colon + 1) : raw
}

function isWithinCompletedSnapshot(
  itemTime: string | null | undefined,
  snapshotTime: string | undefined,
): boolean {
  if (!snapshotTime) return true
  if (!itemTime) return true
  const item = Date.parse(itemTime)
  const cutoff = Date.parse(snapshotTime)
  if (Number.isNaN(item) || Number.isNaN(cutoff)) return true
  return item <= cutoff
}
