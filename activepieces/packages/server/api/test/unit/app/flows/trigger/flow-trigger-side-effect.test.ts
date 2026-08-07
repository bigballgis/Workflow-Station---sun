import { TriggerStrategy } from '@activepieces/pieces-framework'
import {
    ActivepiecesError,
    ApEnvironment,
    EngineResponseStatus,
    ErrorCode,
} from '@activepieces/shared'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockSubmitAndWaitForResponse = vi.fn()
const mockGetPlatformId = vi.fn().mockResolvedValue('platform-1')
const mockDeleteListeners = vi.fn()
const mockRemoveRepeatingJob = vi.fn()

vi.mock('../../../../../src/app/helper/system/system', () => ({
    system: {
        getOrThrow: vi.fn().mockReturnValue(ApEnvironment.PRODUCTION),
        getNumber: vi.fn().mockReturnValue(5),
    },
}))

vi.mock('../../../../../src/app/project/project-service', () => ({
    projectService: vi.fn(() => ({
        getPlatformId: mockGetPlatformId,
    })),
}))

vi.mock('../../../../../src/app/workers/user-interaction-watcher', () => ({
    userInteractionWatcher: {
        submitAndWaitForResponse: (...args: unknown[]) => mockSubmitAndWaitForResponse(...args),
    },
}))

vi.mock('../../../../../src/app/workers/job-queue/job-queue', () => ({
    jobQueue: vi.fn(() => ({
        removeRepeatingJob: mockRemoveRepeatingJob,
    })),
    JobType: { ONE_TIME: 'ONE_TIME', REPEATING: 'REPEATING' },
}))

vi.mock('../../../../../src/app/trigger/app-event-routing/app-event-routing.service', () => ({
    appEventRoutingService: {
        deleteListeners: (...args: unknown[]) => mockDeleteListeners(...args),
    },
}))

import { flowTriggerSideEffect } from '../../../../../src/app/trigger/trigger-source/flow-trigger-side-effect'

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

const BASE_PARAMS = {
    flowId: 'flow-1',
    flowVersionId: 'fv-1',
    pieceName: '@activepieces/piece-test',
    projectId: 'proj-1',
    simulate: false,
}

function makePollingTrigger() {
    return {
        name: 'test_trigger',
        displayName: 'Test Trigger',
        description: 'Test',
        props: {},
        requireAuth: false,
        type: TriggerStrategy.POLLING,
        sampleData: {},
        testStrategy: 'TEST_FUNCTION',
    } as any
}

function makeManualTrigger() {
    return {
        ...makePollingTrigger(),
        type: TriggerStrategy.MANUAL,
    }
}

function okEngineResponse() {
    return {
        status: EngineResponseStatus.OK,
        response: {},
        error: undefined,
    }
}

function failedEngineResponse() {
    return {
        status: EngineResponseStatus.ERROR,
        response: undefined,
        error: 'Engine failed',
    }
}

describe('flowTriggerSideEffect', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockGetPlatformId.mockResolvedValue('platform-1')
    })

    describe('HERMES-PATCH-012 — APP_WEBHOOK 启用即 fail-loud', () => {
        it('拒绝启用 APP_WEBHOOK 触发器，报错指名 piece 与 flow', async () => {
            mockSubmitAndWaitForResponse.mockResolvedValue(okEngineResponse())

            const error = await flowTriggerSideEffect(mockLog).enable({
                ...BASE_PARAMS,
                pieceTrigger: {
                    ...makePollingTrigger(),
                    type: TriggerStrategy.APP_WEBHOOK,
                },
                simulate: false,
            } as any).catch((e: unknown) => e)

            expect(error).toBeInstanceOf(ActivepiecesError)
            // ActivepiecesError 的 message 就是错误码，可读文案在 params.message 里。
            expect((error as ActivepiecesError).error.code).toBe(ErrorCode.FEATURE_DISABLED)
            // 报错必须带上排查所需的三样东西，否则等于换了个说法的 404。
            const detail = (error as ActivepiecesError).error.params.message
            expect(detail).toContain('HERMES-PATCH-012')
            expect(detail).toContain('@activepieces/piece-test')
            expect(detail).toContain('flow-1')
        })

        it('其余策略不受影响 —— MANUAL 仍能正常启用', async () => {
            mockSubmitAndWaitForResponse.mockResolvedValue(okEngineResponse())

            const result = await flowTriggerSideEffect(mockLog).enable({
                ...BASE_PARAMS,
                pieceTrigger: makeManualTrigger(),
                simulate: false,
            } as any)

            expect(result.scheduleOptions).toBeUndefined()
        })

        // disable 刻意不拦：存量 app_event_routing 行必须还能清掉，
        // 否则 012 之前建过监听器的项目会永远留着孤儿行。
        it('disable 仍会删除监听器，不被 fail-loud 挡住', async () => {
            mockSubmitAndWaitForResponse.mockResolvedValue(okEngineResponse())

            await flowTriggerSideEffect(mockLog).disable({
                ...BASE_PARAMS,
                pieceTrigger: {
                    ...makePollingTrigger(),
                    type: TriggerStrategy.APP_WEBHOOK,
                },
                ignoreError: false,
            })

            expect(mockDeleteListeners).toHaveBeenCalledWith({
                projectId: 'proj-1',
                flowId: 'flow-1',
            })
        })
    })

    describe('disable', () => {
        it('should complete successfully when engine responds OK', async () => {
            mockSubmitAndWaitForResponse.mockResolvedValue(okEngineResponse())

            await flowTriggerSideEffect(mockLog).disable({
                ...BASE_PARAMS,
                pieceTrigger: makeManualTrigger(),
                ignoreError: false,
            })

            expect(mockSubmitAndWaitForResponse).toHaveBeenCalledOnce()
        })

        it('should throw when engine response is bad and ignoreError is false', async () => {
            mockSubmitAndWaitForResponse.mockResolvedValue(failedEngineResponse())

            await expect(
                flowTriggerSideEffect(mockLog).disable({
                    ...BASE_PARAMS,
                    pieceTrigger: makeManualTrigger(),
                    ignoreError: false,
                }),
            ).rejects.toThrow(ActivepiecesError)
        })

        it('should not throw when engine response is bad and ignoreError is true', async () => {
            mockSubmitAndWaitForResponse.mockResolvedValue(failedEngineResponse())

            await flowTriggerSideEffect(mockLog).disable({
                ...BASE_PARAMS,
                pieceTrigger: makeManualTrigger(),
                ignoreError: true,
            })
        })

        it('should throw when submitAndWaitForResponse throws and ignoreError is false', async () => {
            mockSubmitAndWaitForResponse.mockRejectedValue(
                new ActivepiecesError({
                    code: ErrorCode.ENGINE_OPERATION_FAILURE,
                    params: { message: 'Worker did not respond within the safety timeout' },
                }),
            )

            await expect(
                flowTriggerSideEffect(mockLog).disable({
                    ...BASE_PARAMS,
                    pieceTrigger: makeManualTrigger(),
                    ignoreError: false,
                }),
            ).rejects.toThrow(ActivepiecesError)
        })

        it('should not throw when submitAndWaitForResponse throws and ignoreError is true', async () => {
            mockSubmitAndWaitForResponse.mockRejectedValue(
                new ActivepiecesError({
                    code: ErrorCode.ENGINE_OPERATION_FAILURE,
                    params: { message: 'Worker did not respond within the safety timeout' },
                }),
            )

            await flowTriggerSideEffect(mockLog).disable({
                ...BASE_PARAMS,
                pieceTrigger: makeManualTrigger(),
                ignoreError: true,
            })

            expect(mockLog.warn).toHaveBeenCalledWith(
                expect.objectContaining({ flowId: 'flow-1' }),
                expect.stringContaining('Ignored error'),
            )
        })

        it('should still remove repeating job for polling trigger when engine call fails and ignoreError is true', async () => {
            mockSubmitAndWaitForResponse.mockRejectedValue(new Error('timeout'))

            await flowTriggerSideEffect(mockLog).disable({
                ...BASE_PARAMS,
                pieceTrigger: makePollingTrigger(),
                ignoreError: true,
            })

            expect(mockRemoveRepeatingJob).toHaveBeenCalledWith({
                flowVersionId: 'fv-1',
            })
        })

        it('should still delete app event listeners when engine call fails and ignoreError is true', async () => {
            mockSubmitAndWaitForResponse.mockRejectedValue(new Error('timeout'))

            await flowTriggerSideEffect(mockLog).disable({
                ...BASE_PARAMS,
                pieceTrigger: {
                    ...makePollingTrigger(),
                    type: TriggerStrategy.APP_WEBHOOK,
                },
                ignoreError: true,
            })

            expect(mockDeleteListeners).toHaveBeenCalledWith({
                projectId: 'proj-1',
                flowId: 'flow-1',
            })
        })
    })
})
