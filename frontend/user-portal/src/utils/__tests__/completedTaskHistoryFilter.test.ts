import { describe, expect, it } from 'vitest'
import {
  shouldKeepCompletedHistoryItem,
  type CompletedHistoryFilterItem,
} from '../completedTaskHistoryFilter'

const snapshotOpts = {
  isCompletedTask: true,
  hasSnapshotRoute: true,
  snapshotTaskId: 'task-confirm',
  snapshotTime: '2026-09-10T04:10:34.104Z',
}

function item(partial: CompletedHistoryFilterItem): CompletedHistoryFilterItem {
  return partial
}

describe('shouldKeepCompletedHistoryItem', () => {
  it('keeps everything when not a completed snapshot route', () => {
    expect(shouldKeepCompletedHistoryItem(
      item({ taskId: 'later', operationTime: '2026-09-10T05:00:00.000Z' }),
      { isCompletedTask: false, hasSnapshotRoute: true },
    )).toBe(true)
  })

  it('keeps SEND that finished after the human-task snapshot cutoff', () => {
    const send = item({
      activityType: 'serviceTask',
      operationType: 'SEND',
      operationTime: '2026-09-10T04:10:43.742Z',
    })
    expect(shouldKeepCompletedHistoryItem(send, snapshotOpts)).toBe(true)
  })

  it('drops a later user task even if an endEvent has already finished', () => {
    const later = item({
      taskId: 'task-next',
      activityType: 'userTask',
      operationType: 'APPROVE',
      operationTime: '2026-09-10T04:20:00.000Z',
    })
    expect(shouldKeepCompletedHistoryItem(later, snapshotOpts)).toBe(false)
  })

  it('keeps an endEvent that finished after the human-task snapshot cutoff', () => {
    const end = item({
      activityType: 'endEvent',
      operationType: 'APPROVE',
      operationTime: '2026-09-10T04:10:43.761Z',
    })
    expect(shouldKeepCompletedHistoryItem(end, snapshotOpts)).toBe(true)
  })
})
