import { MachineInformation, WorkerGroupScope, WorkerMachineStatus, WorkerMachineType } from '@activepieces/shared'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { WorkerMachine, workerMachineCache } from '../../../../../src/app/workers/machine/machine-cache'
import { machineService } from '../../../../../src/app/workers/machine/machine-service'

let inMemoryStore: Map<string, WorkerMachine>

vi.mock('../../../../../src/app/workers/machine/machine-cache', () => ({
    workerMachineCache: () => ({
        async find(): Promise<WorkerMachine[]> {
            return Array.from(inMemoryStore.values())
        },
        async delete(ids: string[]): Promise<void> {
            for (const id of ids) {
                inMemoryStore.delete(id)
            }
        },
        async upsert(worker: { id: string } & Partial<Omit<WorkerMachine, 'id'>>): Promise<void> {
            const now = new Date().toISOString()
            const existing = inMemoryStore.get(worker.id)
            if (existing) {
                inMemoryStore.set(worker.id, { ...existing, ...worker, updated: now })
            }
            else {
                inMemoryStore.set(worker.id, { ...worker, updated: now, created: now } as WorkerMachine)
            }
        },
    }),
}))

/**
 * HERMES / CE scope note (AG-EE, EE_REMOVAL_PLAN G5).
 *
 * `machineService.list()` asks `workerGroupService().getWorkerGroupId({ platformId })`,
 * which is now the CE stub (src/app/workers/worker-group-stub.ts) and always answers
 * `null` — this fork has no platform worker groups. The stub is left unmocked on purpose
 * so these cases exercise the real CE branch:
 *   - PLATFORM-scope workers are never visible to anyone (they need a platform group),
 *   - PROJECT-scope workers (still live in CE — a worker self-declares its group over the
 *     socket handshake, see machine-controller.ts) are visible to every platform,
 *   - SHARED and legacy (no type) workers are visible to every platform.
 * The two EE cases that were here — "dedicated workers only for the matching platform"
 * and "only dedicated workers when the platform has a worker group" — were deleted with
 * the EE service that assigned those groups.
 */

const mockLogger = {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
    child: vi.fn(),
} as never

function fakeMachineInfo(workerId: string): MachineInformation {
    return {
        workerId,
        cpuUsagePercentage: 0,
        ramUsagePercentage: 0,
        totalAvailableRamInBytes: 0,
        totalCpuCores: 1,
        ip: '127.0.0.1',
        diskInfo: { total: 100, free: 50, used: 50, percentage: 50 },
        workerProps: {},
    }
}

describe('machineService.list — platform filtering', () => {
    beforeEach(() => {
        inMemoryStore = new Map()
    })

    it('should return shared workers for any platform', async () => {
        await workerMachineCache().upsert({
            id: 'shared-1',
            information: fakeMachineInfo('shared-1'),
            type: 'SHARED',
        })

        const result = await machineService(mockLogger).list('platform-A')

        expect(result).toHaveLength(1)
        expect(result[0].id).toBe('shared-1')
        expect(result[0].type).toBe(WorkerMachineType.SHARED)
        expect(result[0].status).toBe(WorkerMachineStatus.ONLINE)
    })

    // CE: no platform ever has a worker group, so a PLATFORM-scope worker matches nobody —
    // not even a platform whose id looks like the group's. This is the branch the deleted
    // EE cases used to turn OFF.
    it('should not return platform-scope dedicated workers to any platform', async () => {
        await workerMachineCache().upsert({
            id: 'dedicated-other',
            information: fakeMachineInfo('dedicated-other'),
            type: 'DEDICATED',
            workerGroupScope: WorkerGroupScope.PLATFORM,
            workerGroupId: 'group-other',
        })

        expect(await machineService(mockLogger).list('platform-mine')).toHaveLength(0)
        expect(await machineService(mockLogger).list('group-other')).toHaveLength(0)
    })

    it('should return shared workers when platform has no worker group', async () => {
        await workerMachineCache().upsert({
            id: 'shared-1',
            information: fakeMachineInfo('shared-1'),
            type: 'SHARED',
        })

        await workerMachineCache().upsert({
            id: 'dedicated-other',
            information: fakeMachineInfo('dedicated-other'),
            type: 'DEDICATED',
            workerGroupScope: WorkerGroupScope.PLATFORM,
            workerGroupId: 'group-Y',
        })

        const result = await machineService(mockLogger).list('platform-no-group')
        expect(result).toHaveLength(1)
        expect(result[0].id).toBe('shared-1')
        expect(result[0].type).toBe(WorkerMachineType.SHARED)
    })

    it('should return project-scope workers to any platform', async () => {
        await workerMachineCache().upsert({
            id: 'project-worker',
            information: fakeMachineInfo('project-worker'),
            type: 'DEDICATED',
            workerGroupScope: WorkerGroupScope.PROJECT,
            workerGroupId: '1cpu_machine',
        })

        const result = await machineService(mockLogger).list('any-platform');
        expect(result).toHaveLength(1)
        expect(result[0].id).toBe('project-worker')
        expect(result[0].workerGroupScope).toBe(WorkerGroupScope.PROJECT)
    })

    it('should include legacy workers with no type as shared', async () => {
        await workerMachineCache().upsert({
            id: 'legacy-worker',
            information: fakeMachineInfo('legacy-worker'),
        })

        const result = await machineService(mockLogger).list('any-platform')
        expect(result).toHaveLength(1)
        expect(result[0].id).toBe('legacy-worker')
        expect(result[0].type).toBe(WorkerMachineType.SHARED)
    })
})
