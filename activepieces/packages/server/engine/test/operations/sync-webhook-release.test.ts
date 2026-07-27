import { FlowRunStatus, isFlowRunStateTerminal } from '@activepieces/shared'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { mockSendFlowResponse } = vi.hoisted(() => ({
    mockSendFlowResponse: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('../../src/lib/worker-socket', () => ({
    workerSocket: {
        getWorkerClient: vi.fn().mockReturnValue({
            sendFlowResponse: mockSendFlowResponse,
        }),
    },
}))

import { syncWebhookRelease } from '../../src/lib/operations/sync-webhook-release'

/**
 * HERMES-PATCH-007 —— 见 docs/ap-integration/HERMES_PATCHES.md。
 *
 * <p>护的不变量：<b>只有 run 真的结束了才释放调用方</b>。释放早了，阻塞在 `handleSync` 的
 * 调用方会在 flow 还活着的时候收到"没有响应"的 204——比补丁本来要解决的 300s 干等更糟，
 * 因为它是错的而不只是慢。
 *
 * <p>非终态那一支在 dev 环境测不到：能暂停的 piece（delay / approval / subflows）都不在
 * 已装白名单里，唯一装了的 `piece-webhook` 又是「先答后停」（`responseToSend` 在同一步就
 * 发出去了），所以那里即使判断写反也观察不到症状。一旦哪天 delay 进了白名单，它立刻
 * 承重——故在此逐状态锁定，而不是留给一次性手测。
 */
describe('HERMES-PATCH-007 — sync webhook 终态释放', () => {
    const IDS = { workerHandlerId: 'handler-1', httpRequestId: 'req-1' }

    beforeEach(() => {
        mockSendFlowResponse.mockReset()
        mockSendFlowResponse.mockResolvedValue(undefined)
    })

    /** 全量枚举：新增状态时这里会自然带上，不会漏判。 */
    const ALL_STATUSES = Object.values(FlowRunStatus)
    const NON_TERMINAL = ALL_STATUSES.filter(
        status => !isFlowRunStateTerminal({ status, ignoreInternalError: false }),
    )
    const TERMINAL = ALL_STATUSES.filter(
        status => isFlowRunStateTerminal({ status, ignoreInternalError: false }),
    )

    it('枚举本身有效——两边都不为空，否则下面的断言是空转', () => {
        expect(NON_TERMINAL).toContain(FlowRunStatus.PAUSED)
        expect(NON_TERMINAL).toContain(FlowRunStatus.QUEUED)
        expect(TERMINAL).toContain(FlowRunStatus.FAILED)
        expect(TERMINAL).toContain(FlowRunStatus.SUCCEEDED)
    })

    it.each(NON_TERMINAL)('非终态 %s 不释放 —— 响应要等 resume 之后才来', async (status) => {
        await syncWebhookRelease.onRunSettled({ ...IDS, status })
        expect(mockSendFlowResponse).not.toHaveBeenCalled()
    })

    it.each(TERMINAL)('终态 %s 释放一次，状态 204 —— 与 handleSync 超时兜底同一结果', async (status) => {
        await syncWebhookRelease.onRunSettled({ ...IDS, status })

        expect(mockSendFlowResponse).toHaveBeenCalledTimes(1)
        expect(mockSendFlowResponse).toHaveBeenCalledWith({
            ...IDS,
            runResponse: { status: 204, body: {}, headers: {} },
        })
    })

    it('没有 workerHandlerId / httpRequestId 就没人在等，不发', async () => {
        const status = FlowRunStatus.FAILED

        await syncWebhookRelease.onRunSettled({ workerHandlerId: null, httpRequestId: null, status })
        await syncWebhookRelease.onRunSettled({ workerHandlerId: 'handler-1', httpRequestId: null, status })
        await syncWebhookRelease.onRunSettled({ workerHandlerId: null, httpRequestId: 'req-1', status })

        expect(mockSendFlowResponse).not.toHaveBeenCalled()
    })

    it('发布失败不外溢 —— run 已报完状态，超时仍是兜底', async () => {
        mockSendFlowResponse.mockRejectedValue(new Error('socket closed'))

        await expect(
            syncWebhookRelease.onRunSettled({ ...IDS, status: FlowRunStatus.FAILED }),
        ).resolves.toBeUndefined()
        expect(mockSendFlowResponse).toHaveBeenCalledTimes(1)
    })
})
