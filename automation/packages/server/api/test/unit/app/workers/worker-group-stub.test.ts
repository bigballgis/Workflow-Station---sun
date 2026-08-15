import { FastifyBaseLogger } from 'fastify'
import { describe, expect, it, vi } from 'vitest'
import { workerGroupService } from '../../../../src/app/workers/worker-group-stub'

/**
 * HERMES / CE contract (AG-EE, EE_REMOVAL_PLAN G5).
 *
 * The EE `worker-group.service` was deleted along with its dedicated-worker / canary
 * concept, and every caller now imports this stub instead. Its return values ARE the
 * product behaviour of this fork — every worker is SHARED and no platform is canary —
 * so they are pinned here. If someone reintroduces real worker groups, the callers'
 * behaviour changes silently (worker list filtering, queue routing, canary proxying);
 * this test is what stops that from landing unnoticed.
 *
 * The EE tests that covered the deleted service (test/unit/app/core/canary/
 * worker-group.service.test.ts) were removed — they mocked a module that no longer exists.
 */

const log = { error: vi.fn(), warn: vi.fn(), info: vi.fn(), debug: vi.fn() } as unknown as FastifyBaseLogger

describe('workerGroupService (CE stub)', () => {
    it('reports no worker group for any platform — all workers are SHARED', async () => {
        await expect(workerGroupService(log).getWorkerGroupId({ platformId: 'any-platform' })).resolves.toBeNull()
    })

    it('reports no platform as canary — canary routing can never engage', async () => {
        await expect(workerGroupService(log).isCanaryPlatform({ platformId: 'any-platform' })).resolves.toBe(false)
    })

    it('reports worker groups as disabled — job routing always takes the shared-queue path', async () => {
        await expect(workerGroupService(log).isWorkerGroupsEnabled({ platformId: 'any-platform' })).resolves.toBe(false)
    })
})
