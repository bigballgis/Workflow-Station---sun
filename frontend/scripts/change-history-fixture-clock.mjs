import assert from 'node:assert/strict'

/** Pass the snapshot epoch parsed in the Playwright page, so its timezone matches the UI. */
export function createHistoryFixtureClock(snapshotEpoch) {
  assert.ok(Number.isFinite(snapshotEpoch), 'CH_TASK_URL must contain a valid snapshotTime')
  return {
    at: seconds => new Date(snapshotEpoch - 60 * 60 * 1000 + seconds * 1000).toISOString(),
    future: new Date(snapshotEpoch + 1000).toISOString(),
  }
}
