import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
    ActivepiecesError,
    ErrorCode,
    ExecutionType,
    FlowActionType,
    FlowRunStatus,
    FlowTriggerType,
    FlowVersionState,
    StreamStepProgress,
    RunEnvironment,
    WorkerJobType,
} from '@activepieces/shared'
import type { ExecuteFlowJobData, FlowVersion } from '@activepieces/shared'

const mockGetVersion = vi.fn()

vi.mock('../../../../src/lib/cache/flow/flow-cache', () => ({
    flowCache: () => ({
        getVersion: mockGetVersion,
    }),
}))

vi.mock('../../../../src/lib/config/worker-settings', () => ({
    workerSettings: {
        getSettings: vi.fn().mockReturnValue({ FLOW_TIMEOUT_SECONDS: 600 }),
    },
}))

vi.mock('../../../../src/lib/execute/utils/flow-helpers', () => ({
    provisionFlowPieces: vi.fn().mockResolvedValue(true),
}))

import { executeFlowJob } from '../../../../src/lib/execute/jobs/execute-flow'
import { JobResultKind } from '../../../../src/lib/execute/types'

function makeFlowVersion(): FlowVersion {
    return {
        id: 'fv-1',
        created: '2024-01-01T00:00:00Z',
        updated: '2024-01-01T00:00:00Z',
        flowId: 'flow-1',
        displayName: 'Test Flow',
        trigger: {
            name: 'trigger_1',
            valid: true,
            displayName: 'Gmail Trigger',
            lastUpdatedDate: '2024-01-01T00:00:00Z',
            type: FlowTriggerType.PIECE,
            settings: {
                pieceName: '@activepieces/piece-gmail',
                pieceVersion: '~0.1.0',
                triggerName: 'new_email',
                input: {},
                propertySettings: {},
            },
            nextAction: {
                name: 'step_1',
                valid: true,
                displayName: 'Slack Action',
                lastUpdatedDate: '2024-01-01T00:00:00Z',
                type: FlowActionType.PIECE,
                settings: {
                    pieceName: '@activepieces/piece-slack',
                    pieceVersion: '~0.2.0',
                    actionName: 'send_message',
                    input: {},
                    propertySettings: {},
                },
            },
        },
        updatedBy: null,
        valid: true,
        schemaVersion: null,
        agentIds: [],
        state: FlowVersionState.DRAFT,
        connectionIds: [],
        backupFiles: null,
        notes: [],
    }
}

function makeResumeJobData(overrides?: Partial<ExecuteFlowJobData>): ExecuteFlowJobData {
    return {
        projectId: 'proj-1',
        platformId: 'plat-1',
        jobType: WorkerJobType.EXECUTE_FLOW,
        environment: RunEnvironment.PRODUCTION,
        schemaVersion: 4,
        flowId: 'flow-1',
        flowVersionId: 'fv-1',
        runId: 'run-1',
        payload: { type: 'inline', value: {} },
        executionType: ExecutionType.RESUME,
        streamStepProgress: StreamStepProgress.NONE,
        logsUploadUrl: 'http://example.com/upload',
        logsFileId: 'logs-file-1',
        ...overrides,
    }
}

function makeMockContext(apiOverrides?: Record<string, vi.Mock>) {
    const mockSandbox = {
        start: vi.fn(),
        execute: vi.fn().mockResolvedValue({ status: 'OK' }),
    }
    return {
        log: {
            info: vi.fn(),
            warn: vi.fn(),
            error: vi.fn(),
            debug: vi.fn(),
        },
        apiClient: {
            getPayloadFile: vi.fn(),
            uploadRunLog: vi.fn(),
            sendFlowResponse: vi.fn().mockResolvedValue(undefined),
            ...apiOverrides,
        },
        sandboxManager: {
            acquire: vi.fn().mockReturnValue(mockSandbox),
            release: vi.fn(),
            invalidate: vi.fn(),
        },
        engineToken: 'test-token',
        internalApiUrl: 'http://localhost:3000',
        publicApiUrl: 'http://localhost:4200',
        mockSandbox,
    } as any
}

describe('executeFlowJob', () => {
    beforeEach(() => {
        mockGetVersion.mockResolvedValue(makeFlowVersion())
    })

    describe('payload pass-through (no worker-side fetch)', () => {
        it('does not call getPayloadFile in the worker — payload resolution is deferred to the engine', async () => {
            const ctx = makeMockContext()
            const data = makeResumeJobData({
                executionType: ExecutionType.BEGIN,
                payload: { type: 'ref', fileId: 'huge-file-1' },
            })

            await executeFlowJob.execute(ctx, data)

            expect(ctx.apiClient.getPayloadFile).not.toHaveBeenCalled()
        })

        it('forwards the JobPayload ref unchanged to the engine for BEGIN', async () => {
            const ctx = makeMockContext()
            const data = makeResumeJobData({
                executionType: ExecutionType.BEGIN,
                payload: { type: 'ref', fileId: 'huge-file-1' },
            })

            await executeFlowJob.execute(ctx, data)

            const operation = ctx.mockSandbox.execute.mock.calls[0][1]
            expect(operation.executionType).toBe(ExecutionType.BEGIN)
            expect(operation.triggerPayload).toEqual({ type: 'ref', fileId: 'huge-file-1' })
            expect(operation.executionState).toBeUndefined()
        })

        it('forwards the JobPayload ref unchanged to the engine for RESUME and never reads logsFileId', async () => {
            const ctx = makeMockContext()
            const data = makeResumeJobData({
                payload: { type: 'ref', fileId: 'resume-payload-1' },
                logsFileId: 'logs-file-1',
            })

            await executeFlowJob.execute(ctx, data)

            const operation = ctx.mockSandbox.execute.mock.calls[0][1]
            expect(operation.executionType).toBe(ExecutionType.RESUME)
            expect(operation.resumePayload).toEqual({ type: 'ref', fileId: 'resume-payload-1' })
            expect(operation.logsFileId).toBe('logs-file-1')
            expect(operation.executionState).toBeUndefined()
            expect(ctx.apiClient.getPayloadFile).not.toHaveBeenCalled()
        })
    })

    describe('RESUME validation', () => {
        it('still throws when logsFileId is missing for RESUME', async () => {
            const ctx = makeMockContext()
            const data = makeResumeJobData({ logsFileId: undefined as unknown as string })

            try {
                await executeFlowJob.execute(ctx, data)
                expect.fail('should have thrown')
            }
            catch (e) {
                expect(e).toBeInstanceOf(ActivepiecesError)
                expect((e as ActivepiecesError).error.code).toBe(ErrorCode.RESUME_LOGS_FILE_MISSING)
            }

            expect(ctx.apiClient.uploadRunLog).toHaveBeenCalledWith(
                expect.objectContaining({ status: FlowRunStatus.INTERNAL_ERROR }),
            )
        })
    })

    describe('missing piece handling', () => {
        it('marks run as FAILED and skips sandbox when flow version is not found', async () => {
            mockGetVersion.mockResolvedValue(null)

            const ctx = makeMockContext()
            const data = makeResumeJobData({ executionType: ExecutionType.BEGIN })

            const result = await executeFlowJob.execute(ctx, data)

            expect(result.kind).toBe(JobResultKind.FIRE_AND_FORGET)

            expect(ctx.apiClient.uploadRunLog).toHaveBeenCalledWith(
                expect.objectContaining({ status: FlowRunStatus.FAILED }),
            )

            expect(ctx.sandboxManager.acquire).not.toHaveBeenCalled()
        })
    })
    /**
     * HERMES-PATCH-008 —— 见 docs/ap-integration/HERMES_PATCHES.md。
     *
     * engine 侧的释放（HERMES-PATCH-007）够不到引擎启动之前就死掉的 run：piece 安装失败、
     * flow version 缺失、sandbox 超时 / OOM 都终结在 worker 里。少了这一半，调用方仍要等满
     * AP_WEBHOOK_TIMEOUT_SECONDS —— 这正是 dev 实测时 biz-calendar flow 打了 engine 补丁
     * 后仍然 300s 的原因。
     */
    describe('HERMES-PATCH-008 — 引擎启动前就终结的 run 也释放 sync webhook', () => {
        const SYNC_IDS = { workerHandlerId: 'handler-1', httpRequestId: 'req-1' }
        const NO_CONTENT = { status: 204, body: {}, headers: {} }

        it('flow version 缺失 —— 引擎没跑起来,仍然释放', async () => {
            mockGetVersion.mockResolvedValue(null)
            const ctx = makeMockContext()

            await executeFlowJob.execute(ctx, makeResumeJobData({
                executionType: ExecutionType.BEGIN,
                ...SYNC_IDS,
            }))

            expect(ctx.sandboxManager.acquire).not.toHaveBeenCalled()
            expect(ctx.apiClient.sendFlowResponse).toHaveBeenCalledTimes(1)
            expect(ctx.apiClient.sendFlowResponse).toHaveBeenCalledWith({
                ...SYNC_IDS,
                runResponse: NO_CONTENT,
            })
        })

        it('没有 workerHandlerId / httpRequestId 就没人在等,不发', async () => {
            mockGetVersion.mockResolvedValue(null)
            const ctx = makeMockContext()

            await executeFlowJob.execute(ctx, makeResumeJobData({ executionType: ExecutionType.BEGIN }))

            expect(ctx.apiClient.uploadRunLog).toHaveBeenCalled()
            expect(ctx.apiClient.sendFlowResponse).not.toHaveBeenCalled()
        })

        it('run 正常跑完时 worker 不插手 —— 响应由 engine 侧决定', async () => {
            const ctx = makeMockContext()

            await executeFlowJob.execute(ctx, makeResumeJobData({
                executionType: ExecutionType.BEGIN,
                ...SYNC_IDS,
            }))

            expect(ctx.apiClient.uploadRunLog).not.toHaveBeenCalled()
            expect(ctx.apiClient.sendFlowResponse).not.toHaveBeenCalled()
        })

        it('发布失败不改判已完成的 run —— best-effort,超时仍是兜底', async () => {
            mockGetVersion.mockResolvedValue(null)
            const ctx = makeMockContext({
                sendFlowResponse: vi.fn().mockRejectedValue(new Error('api down')),
            })

            const result = await executeFlowJob.execute(ctx, makeResumeJobData({
                executionType: ExecutionType.BEGIN,
                ...SYNC_IDS,
            }))

            expect(result.kind).toBe(JobResultKind.FIRE_AND_FORGET)
            expect(ctx.log.error).toHaveBeenCalled()
        })
    })
})
