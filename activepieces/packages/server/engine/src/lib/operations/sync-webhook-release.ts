import { FlowRunStatus, isFlowRunStateTerminal, isNil, tryCatch } from '@activepieces/shared'
import { workerSocket } from '../worker-socket'

/**
 * HERMES-PATCH-007 — 让阻塞在 sync webhook 上的调用方在 run 结束时立刻拿到结果。
 *
 * <p>AP 只在 "Return Response" 一步发布 sync 响应（见 piece-executor）。跑挂、超时、或
 * 压根没有那一步的 run 什么都不发，于是阻塞在 `handleSync` 的调用方要等满
 * `WEBHOOK_TIMEOUT_MS`（本环境 300s）才拿到兜底 204。HERMES 的 BPMN 服务任务能正确
 * 读懂那个 204（flow 没产出响应），但要在 flow 实际死掉五分钟后才知道，期间流程实例
 * 一直卡着。
 *
 * <p>此刻 run 已终止，它可能发布的响应早就发完了，所以在这里释放只改变延迟、不改变结果。
 * 重复发布无害：`oneTimeListener` 首次响应即自注销，无监听者的 publish 是空操作。
 *
 * <p><b>非终态必须继续等</b>（PAUSED 停在 waitpoint / 审批、QUEUED 还没跑）——响应要等
 * resume 之后才来，提前释放会让调用方在 flow 还活着时收到"没有响应"，比干等更糟。
 * 这条判断由 `test/operations/sync-webhook-release.test.ts` 逐状态锁定：dev 环境测不到
 * PAUSED（能暂停的 delay / approval / subflows 都不在已装白名单里，唯一装了的
 * piece-webhook 是「先答后停」）。
 *
 * <p>worker 侧有一份对应实现（`execute/jobs/execute-flow.ts`），覆盖引擎启动之前就死掉
 * 的 run；两者都需要，见 HERMES_PATCHES.md。
 */
export const syncWebhookRelease = {
    async onRunSettled({ workerHandlerId, httpRequestId, status }: ReleaseParams): Promise<void> {
        if (isNil(workerHandlerId) || isNil(httpRequestId)) {
            return
        }
        if (!isFlowRunStateTerminal({ status, ignoreInternalError: false })) {
            return
        }
        // Best-effort on purpose: the run is already finished and reported. Throwing here would
        // turn a completed run into an INTERNAL_ERROR over a latency optimisation, and the
        // webhook timeout still delivers the same 204 if this never lands.
        const { error } = await tryCatch(() => workerSocket.getWorkerClient().sendFlowResponse({
            workerHandlerId,
            httpRequestId,
            runResponse: {
                status: NO_CONTENT_STATUS,
                body: {},
                headers: {},
            },
        }))
        if (error) {
            console.error('[HERMES-PATCH-007] Failed to release sync webhook on terminal run', error)
        }
    },
}

/** Matches the fallback `handleSync` returns on timeout, so the caller sees one outcome either way. */
const NO_CONTENT_STATUS = 204

type ReleaseParams = {
    workerHandlerId: string | null
    httpRequestId: string | null
    status: FlowRunStatus
}
