import { describe, expect, it } from 'vitest'
import { createUploadQueue } from '../uploadQueue'

describe('createUploadQueue', () => {
  it('never runs more than concurrency tasks at once', async () => {
    const queue = createUploadQueue(2)
    let inFlight = 0
    let peak = 0
    const job = () => queue.run(async () => {
      inFlight += 1
      peak = Math.max(peak, inFlight)
      await Promise.resolve()
      inFlight -= 1
    })
    await Promise.all([job(), job(), job(), job(), job()])
    expect(peak).toBeLessThanOrEqual(2)
    expect(inFlight).toBe(0)
  })
})
