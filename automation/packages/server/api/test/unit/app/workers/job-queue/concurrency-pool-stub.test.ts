import { FastifyBaseLogger } from 'fastify'
import { describe, expect, it, vi } from 'vitest'
import { concurrencyPoolService } from '../../../../../src/app/workers/job-queue/concurrency-pool-stub'

/**
 * HERMES / CE contract (AG-EE, EE_REMOVAL_PLAN G5).
 *
 * The EE concurrency-pool service was deleted with the tenant/BU quota model (C13).
 * `null` from both methods is what makes the rate limiter fall back to a per-project
 * ZSET keyed by projectId and to DEFAULT_CONCURRENT_JOBS_LIMIT — see
 * interceptors/rate-limiter-interceptor.test.ts, which pins the caller side of this.
 */

const log = { error: vi.fn(), warn: vi.fn(), info: vi.fn(), debug: vi.fn() } as unknown as FastifyBaseLogger

describe('concurrencyPoolService (CE stub)', () => {
    it('assigns no pool to any project — every project is rate limited on its own key', async () => {
        await expect(concurrencyPoolService(log).getProjectPoolId('any-project')).resolves.toBeNull()
    })

    it('has no pool limit — the configured DEFAULT_CONCURRENT_JOBS_LIMIT is the only limit', async () => {
        await expect(concurrencyPoolService(log).getPoolLimit('any-pool')).resolves.toBeNull()
    })
})
