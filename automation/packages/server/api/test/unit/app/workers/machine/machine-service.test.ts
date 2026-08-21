import { ApEnvironment, ExecutionMode, WorkerGroupScope } from '@activepieces/shared'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppSystemProp } from '../../../../../src/app/helper/system/system-props'

vi.mock('../../../../../src/app/workers/machine/machine-cache', () => ({
    workerMachineCache: vi.fn(() => ({
        findOne: vi.fn().mockResolvedValue(null),
        upsert: vi.fn().mockResolvedValue(undefined),
    })),
}))

vi.mock('../../../../../src/app/helper/system/system', () => ({
    system: {
        getOrThrow: vi.fn().mockReturnValue('test-value'),
        getNumberOrThrow: vi.fn().mockReturnValue(60),
        // ENVIRONMENT must read as TESTING: onConnection() invalidates the worker-capacity
        // snapshot, which broadcasts over Redis pub/sub outside a test environment. Returning
        // undefined here (the old mock) sent it down the real-Redis path and every case died
        // with `system.getNumber is not a function` from the redis settings reader.
        get: vi.fn((prop: AppSystemProp) => prop === AppSystemProp.ENVIRONMENT ? ApEnvironment.TESTING : undefined),
        getNumber: vi.fn().mockReturnValue(undefined),
        getBoolean: vi.fn().mockReturnValue(undefined),
    },
}))

vi.mock('../../../../../src/app/helper/domain-helper', () => ({
    domainHelper: {
        getPublicUrl: vi.fn().mockResolvedValue('https://example.com'),
    },
}))

import { system } from '../../../../../src/app/helper/system/system'

const mockLog = {
    info: vi.fn(),
    debug: vi.fn(),
    error: vi.fn(),
    warn: vi.fn(),
    child: vi.fn(),
    fatal: vi.fn(),
    trace: vi.fn(),
    silent: vi.fn(),
    level: 'info',
} as any

const mockHealthcheck = {
    workerId: 'test-worker-1',
    cpuUsagePercentage: 10,
    ramUsagePercentage: 20,
    totalAvailableRamInBytes: 1024,
    diskInfo: {
        total: 1000,
        free: 500,
        used: 500,
        percentage: 50,
    },
}

describe('machineService — execution mode', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.resetModules()
    })

    it('should return system default execution mode for shared workers', async () => {
        vi.mocked(system.getOrThrow).mockReturnValue(ExecutionMode.SANDBOX_PROCESS as any)

        const { machineService: freshMachineService } = await import('../../../../../src/app/workers/machine/machine-service')
        const result = await freshMachineService(mockLog).onConnection(mockHealthcheck)

        expect(result.EXECUTION_MODE).toBe(ExecutionMode.SANDBOX_PROCESS)
    })

    // CE keeps ONE execution mode for every worker: the EE per-worker-group override is gone
    // (AG-EE / G5). Project-scope assignments still exist — a worker declares its group in the
    // socket handshake (machine-controller.ts) — so this pins that even an assigned worker gets
    // the system default rather than anything group-specific.
    it('should return system default execution mode for workers with a group assignment', async () => {
        vi.mocked(system.getOrThrow).mockReturnValue(ExecutionMode.SANDBOX_CODE_AND_PROCESS as any)

        const { machineService: freshMachineService } = await import('../../../../../src/app/workers/machine/machine-service')
        const result = await freshMachineService(mockLog).onConnection(mockHealthcheck, {
            scope: WorkerGroupScope.PROJECT,
            id: '1cpu_machine',
        })

        expect(result.EXECUTION_MODE).toBe(ExecutionMode.SANDBOX_CODE_AND_PROCESS)
    })
})
