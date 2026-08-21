import { JobData, WorkerJobType } from '@activepieces/shared'
import { eventDestinationJob } from './jobs/event-destination'
import { executeActionJob } from './jobs/execute-action'
import { executeFlowJob } from './jobs/execute-flow'
import { executePollingJob } from './jobs/execute-polling'
import { executePropertyJob } from './jobs/execute-property'
import { executeTokenRefreshJob } from './jobs/execute-token-refresh'
import { executeTriggerHookJob } from './jobs/execute-trigger-hook'
import { executeValidationJob } from './jobs/execute-validation'
import { executeWebhookJob } from './jobs/execute-webhook'
import { extractPieceInfoJob } from './jobs/extract-piece-info'
import { renewWebhookJob } from './jobs/renew-webhook'
import { resolveConnectionIdentifierJob } from './jobs/resolve-connection-identifier'
import { JobHandler } from './types'

export async function getHandler(jobType: WorkerJobType): Promise<JobHandler<JobData>> {
    const eager = registry[jobType]
    if (eager !== undefined) {
        return eager
    }
    const cached = lazyCache.get(jobType)
    if (cached !== undefined) {
        return cached
    }
    const loader = lazyLoaders[jobType]
    if (loader === undefined) {
        throw new Error(`No handler registered for job type ${jobType}`)
    }
    const handler = await loader()
    lazyCache.set(jobType, handler)
    return handler
}

const registry: Partial<Record<WorkerJobType, JobHandler>> = {
    [WorkerJobType.EXECUTE_FLOW]: executeFlowJob,
    [WorkerJobType.EXECUTE_POLLING]: executePollingJob,
    [WorkerJobType.EXECUTE_WEBHOOK]: executeWebhookJob,
    [WorkerJobType.RENEW_WEBHOOK]: renewWebhookJob,
    [WorkerJobType.EXECUTE_TRIGGER_HOOK]: executeTriggerHookJob,
    [WorkerJobType.EXECUTE_PROPERTY]: executePropertyJob,
    [WorkerJobType.EXECUTE_VALIDATION]: executeValidationJob,
    [WorkerJobType.EXECUTE_RESOLVE_CONNECTION_IDENTIFIER]: resolveConnectionIdentifierJob,
    [WorkerJobType.EXECUTE_TOKEN_REFRESH]: executeTokenRefreshJob,
    [WorkerJobType.EXECUTE_EXTRACT_PIECE_INFORMATION]: extractPieceInfoJob,
    [WorkerJobType.EVENT_DESTINATION]: eventDestinationJob,
    [WorkerJobType.EXECUTE_ACTION]: executeActionJob,
}

// HERMES-PATCH-015 (0.88): the EE agent-run handler (jobs/ee/agent) is deleted with the
// agents/ai domains — it was the worker's only @ai-sdk/mcp consumer. No lazy handler remains;
// an EXECUTE_AGENT_RUN job now fails loudly in getHandler instead of silently no-op'ing.
const lazyLoaders: Partial<Record<WorkerJobType, () => Promise<JobHandler>>> = {}

const lazyCache = new Map<WorkerJobType, JobHandler>()
