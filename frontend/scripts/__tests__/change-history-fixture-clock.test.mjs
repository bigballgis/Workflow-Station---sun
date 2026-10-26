import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createHistoryFixtureClock } from '../change-history-fixture-clock.mjs'

for (const snapshot of ['2026-10-23T18:08:01.277+08:00', '2026-10-26T15:30:45.688+08:00', '2027-01-01T00:05:00Z']) {
  test(`fixtures stay before/after snapshot ${snapshot}`, () => {
    const cutoff = Date.parse(snapshot)
    const clock = createHistoryFixtureClock(cutoff)
    for (const seconds of [0, 60, 70, 80, 90, 100, 401, 402, 480]) {
      assert.ok(Date.parse(clock.at(seconds)) < cutoff)
    }
    assert.equal(Date.parse(clock.future) - cutoff, 1000)
    assert.equal(Date.parse(clock.at(60)) - Date.parse(clock.at(0)), 60000)
  })
}
test('rejects missing or malformed snapshot epochs', () => {
  assert.throws(() => createHistoryFixtureClock(NaN), /valid snapshotTime/)
  assert.throws(() => createHistoryFixtureClock(undefined), /valid snapshotTime/)
})
